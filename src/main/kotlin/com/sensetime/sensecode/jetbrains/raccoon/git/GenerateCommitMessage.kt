package com.sensetime.sensecode.jetbrains.raccoon.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.CodeStreamResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnUIThreadLater
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotEmpty
import kotlinx.coroutines.Job
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import javax.swing.event.AncestorEvent
import kotlin.coroutines.cancellation.CancellationException

class GenerateCommitMessage : AnAction() {
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
            10 * (RaccoonSettingsState.selectedClientConfig.toolwindowModelConfig.maxInputTokens)

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

    private suspend fun generateCommitMessage(
        project: Project,
        changes: List<Change>,
        block: (CodeStreamResponse) -> Unit
    ) {
        val diff: String = requireNotNull(PatchWriter.calculateBaseDirForWritingPatch(project, changes).let { baseDir ->
            IdeaTextPatchBuilder.buildPatch(project, changes, baseDir, false, true).let { patches ->
                val lineSeparator = "\n"
                val writer = MyStringWriter(maxDiffLength)
                UnifiedDiffWriter.write(null, baseDir, patches, writer, lineSeparator, null, null)
                writeBinariesDiff(baseDir, patches.mapNotNull { it as? BinaryFilePatch }, writer, lineSeparator)
                writer.toString()
            }
        }.takeIfNotBlank()) { RaccoonBundle.message("git.commit.warning.noChange") }
        val (client, clientConfig) = RaccoonClientManager.clientAndConfigPair
        val modelConfig = clientConfig.toolwindowModelConfig
        client.requestStream(
            CodeRequest(
                null,
                modelConfig.name,
                listOfNotNull(
                    modelConfig.getSystemPromptPair()?.let {
                        CodeRequest.Message(
                            it.first,
                            it.second
                        )
                    }) + CodeRequest.Message(
                    modelConfig.getRoleString(ModelConfig.Role.USER),
                    "Here are changes of current codebase:\n\n```diff\n$diff\n```\n\nWrite a commit message summarizing these changes, not have to cover erevything, key-points only. Response the content only, limited the message to 50 characters, in plain text format, and without quotation marks."
                ),
                modelConfig.temperature,
                1,
                modelConfig.stop,
                256,
                clientConfig.toolwindowApiPath
            ), block
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
        try {
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
            generateJob = RaccoonClientManager.launchClientJob {
                try {
                    generateCommitMessage(project, changes) { streamResponse ->
                        commitPanel.component.invokeOnUIThreadLater {
                            when (streamResponse) {
                                CodeStreamResponse.Done -> if (commitPanel.commitMessage.isNotBlank()) {
                                    showSuccessMessage(e, RaccoonBundle.message("git.commit.success.generatedDone"))
                                }

                                is CodeStreamResponse.Error -> showErrorMessage(e, streamResponse.error)
                                is CodeStreamResponse.TokenChoices -> streamResponse.choices.firstOrNull()?.token?.takeIf { it.isNotEmpty() }
                                    ?.let { delta ->
                                        commitPanel.commitMessage += delta
                                    }

                                else -> {}
                            }
                        }
                    }
                    commitPanel.component.invokeOnUIThreadLater {
                        if (commitPanel.commitMessage.isBlank()) {
                            showErrorMessage(e, RaccoonBundle.message("git.commit.warning.emptyResponse"))
                        }
                    }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        commitPanel.component.invokeOnUIThreadLater {
                            showErrorMessage(e, t.localizedMessage)
                        }
                    }
                } finally {
                    getCommitMessagePanel(e)?.editorField?.run {
                        invokeOnUIThreadLater {
                            editor?.selectionModel?.removeSelection()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            showErrorMessage(e, t.localizedMessage)
        }
    }
}