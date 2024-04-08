package com.sensetime.sensecode.jetbrains.raccoon.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.patch.GitPatchWriter
import com.intellij.openapi.vcs.changes.patch.PatchWriter
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMUserMessage
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChatChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotEmpty
import kotlinx.coroutines.Job
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import javax.swing.event.AncestorEvent


internal class GenerateCommitMessage : AnAction() {
    private class MyStringWriter(private val maxLength: Int) : StringWriter() {
        private var currentLength = 0
        private fun checkMaxLength(appendLength: Int) {
            currentLength += appendLength
            if (currentLength > maxLength) {
                throw IOException(RaccoonBundle.message("git.commit.warning.tooLong"))
            }
        }

        override fun write(str: String) {
            checkMaxLength(str.length)
            super.write(str)
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            checkMaxLength(len)
            super.write(cbuf, off, len)
        }

        override fun write(str: String, off: Int, len: Int) {
            checkMaxLength(len)
            super.write(str, off, len)
        }
    }

    companion object {
        private val maxDiffLength =
            10 * (RaccoonClient.clientConfig.chatModelConfig.maxInputTokens)

        private fun getCommitMessagePanel(e: AnActionEvent): CommitMessage? =
            e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage

        private fun getCommitPanel(e: AnActionEvent): CheckinProjectPanel? =
            e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel

        private fun List<FilePath>.addToNewChanges(project: Project): List<Change> =
            filterNot { it.isDirectory || it.isNonLocal }.map { Change(null, CurrentContentRevision(it)) }

        // BinaryPatchWriter will write base64 data(large and not necessary)
        private fun writeBinariesDiff(
            basePath: Path,
            patches: List<BinaryFilePatch>,
            writer: Writer,
            lineSeparator: String
        ) {
            // code copy from BinaryPatchWriter.writeBinaries
            patches.forEach { patch ->
                GitPatchWriter.writeGitHeader(writer, basePath, patch, lineSeparator)
                writer.write(lineSeparator)
            }
        }
    }

    private var generateJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    private fun makeLLMRequest(
        project: Project, changes: List<Change>
    ): LLMChatRequest {
        val diff: String = requireNotNull(PatchWriter.calculateBaseDirForWritingPatch(project, changes).let { baseDir ->
            IdeaTextPatchBuilder.buildPatch(project, changes, baseDir, false, true).let { patches ->
                val lineSeparator = "\n"
                val writer = MyStringWriter(maxDiffLength)
                UnifiedDiffWriter.write(null, baseDir, patches, writer, lineSeparator, null, null)
                writeBinariesDiff(baseDir, patches.mapNotNull { it as? BinaryFilePatch }, writer, lineSeparator)
                writer.toString()
            }
        }.takeIfNotBlank()) { RaccoonBundle.message("git.commit.warning.noChange") }

        val modelConfig = RaccoonClient.clientConfig.chatModelConfig
        return LLMChatRequest(
            RaccoonUtils.generateUUID(), maxNewTokens = 256, action = "commit-message", messages = listOfNotNull(
                modelConfig.getLLMSystemMessage(),
                LLMUserMessage("Here are changes of current codebase:\n\n```diff\n$diff\n```\n\nWrite a commit message summarizing these changes, not have to cover erevything, key-points only. Response the content only, limited the message to 50 characters, in plain text format, and without quotation marks.")
            )
        )
    }

    private fun showErrorMessage(e: AnActionEvent, message: String) {
        getCommitMessagePanel(e)?.editorField?.editor?.let {
            RaccoonNotification.popupMessageInBestPositionForEditor(message, it, false)
        }
    }

    private fun showSuccessMessage(e: AnActionEvent, message: String) {
//        showErrorMessage(e, message)
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (null != generateJob?.takeIf { it.isActive }) {
            generateJob = null
            return
        }

        RaccoonExceptions.resultOf {
            generateJob = null
            val project = requireNotNull(e.project) { "project is null" }
            val commitPanel = requireNotNull(getCommitPanel(e)) { "commitPanel is null" }
            val commitWorkflowUi =
                requireNotNull((commitPanel.commitWorkflowHandler as? AbstractCommitWorkflowHandler<*, *>)?.ui) { "commitWorkflowUi is null" }
            val changes = requireNotNull(
                (commitWorkflowUi.getIncludedChanges() + commitWorkflowUi.getIncludedUnversionedFiles()
                    .addToNewChanges(project)).takeIfNotEmpty()
            ) { RaccoonBundle.message("git.commit.warning.noChange") }
            if (changes.size > 100) {
                throw Exception(RaccoonBundle.message("git.commit.warning.tooLong"))
            }
            commitPanel.commitMessage = ""
            commitPanel.component.addAncestorListener(object : AncestorListenerAdapter() {
                override fun ancestorRemoved(event: AncestorEvent?) {
                    generateJob = null
                    super.ancestorRemoved(event)
                }
            })
            generateJob = LLMClientManager.getInstance(project).launchLLMChatJob(
                isEnableNotify = true,
                commitPanel.component,
                makeLLMRequest(project, changes),
                object : LLMClientManager.LLMJobListener<LLMChatChoice, String?>,
                    LLMClient.LLMUsagesResponseListener<LLMChatChoice>() {
                    override fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<LLMChatChoice>) {
                        llmResponse.throwIfError()
                        llmResponse.choices?.firstOrNull()?.token?.takeIf { it.isNotEmpty() }?.let { delta ->
                            commitPanel.commitMessage += delta
                        }
                        super.onResponseInsideEdtAndCatching(llmResponse)
                    }

                    override fun onDoneInsideEdtAndCatching(): String? {
                        if (commitPanel.commitMessage.isNotBlank()) {
                            showSuccessMessage(e, RaccoonBundle.message("git.commit.success.generatedDone"))
                            ApplicationManager.getApplication().messageBus.syncPublisher(
                                RACCOON_STATISTICS_TOPIC
                            ).onGenerateGitCommitMessageFinished()
                        } else {
                            showErrorMessage(e, RaccoonBundle.message("git.commit.warning.emptyResponse"))
                        }
                        return super.onDoneInsideEdtAndCatching()
                    }

                    override fun onFailureWithoutCancellationInsideEdt(t: Throwable) {
                        showErrorMessage(e, t.localizedMessage)
                    }

                    override fun onFinallyInsideEdt() {
                        getCommitMessagePanel(e)?.editorField?.editor?.selectionModel?.removeSelection()
                    }
                }
            )
        }.onFailure {
            showErrorMessage(e, it.localizedMessage)
        }
    }
}
