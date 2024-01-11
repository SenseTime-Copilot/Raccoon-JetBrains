package com.sensetime.sensecode.jetbrains.raccoon.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.patch.GitPatchWriter
import com.intellij.openapi.vcs.changes.patch.PatchWriter
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.CodeStreamResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnUIThreadLater
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotEmpty
import kotlinx.coroutines.Job
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import javax.swing.event.AncestorEvent
import kotlin.coroutines.cancellation.CancellationException

class GenerateCommitMessage : AnAction() {
    // for compatible: because of FakeRevision is changed 231
    private class MyFakeRevision(private val project: Project, private val file: FilePath) : ContentRevision {
        override fun getContent(): String? = file.virtualFile?.let { virtualFile ->
            ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile)?.diffProvider?.createCurrentFileContent(
                virtualFile
            )?.content
        }

        override fun getFile(): FilePath = file

        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL

        override fun toString(): String = file.path
    }

    companion object {
        private fun getCommitMessagePanel(e: AnActionEvent): CommitMessage? =
            e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage

        private fun getCommitPanel(e: AnActionEvent): CheckinProjectPanel? =
            e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel

        private fun List<FilePath>.addToNewChanges(project: Project): List<Change> =
            filterNot { it.isDirectory || it.isNonLocal }.map { Change(null, MyFakeRevision(project, it)) }

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
                val writer = StringWriter()
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
        showErrorMessage(e, message)
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
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        commitPanel.component.invokeOnUIThreadLater {
                            showErrorMessage(e, t.localizedMessage)
                        }
                    }
                } finally {
                    commitPanel.component.invokeOnUIThreadLater {
                        if (commitPanel.commitMessage.isBlank()) {
                            showErrorMessage(e, RaccoonBundle.message("git.commit.warning.emptyResponse"))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            showErrorMessage(e, t.localizedMessage)
        }
    }
}