package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank

class InsertedAtCursorAction(private val editor: Editor, private val language: String) : AnAction(
    RaccoonBundle.message("codes.actions.insertedAtCursor.name"),
    RaccoonBundle.message("codes.actions.insertedAtCursor.description"),
    AllIcons.Actions.MoveToButton
) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { project ->
            FileEditorManager.getInstance(project).selectedTextEditor?.let { dstEditor ->
                editor.document.text.letIfNotBlank { code ->
                    dstEditor.selectionModel.let { selectionModel ->
                        WriteCommandAction.runWriteCommandAction(project) {
                            dstEditor.document.replaceString(
                                selectionModel.selectionStart,
                                selectionModel.selectionEnd,
                                code
                            )
                        }
                        selectionModel.removeSelection()
                        dstEditor.contentComponent.requestFocus()
                        ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                            .onToolWindowCodeInserted(language)
                    }
                }
            }
        }
    }
}