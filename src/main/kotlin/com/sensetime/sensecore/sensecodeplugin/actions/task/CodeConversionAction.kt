package com.sensetime.sensecore.sensecodeplugin.actions.task

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import javax.swing.ListSelectionModel

class CodeConversionAction : CodeTaskActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        e.getData(CommonDataKeys.EDITOR)?.let { editor ->
            getEditorSelectedText(editor)?.let { code ->
                JBPopupFactory.getInstance().createPopupChooserBuilder(LANGUAGES)
                    .setVisibleRowCount(7)
                    .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                    .setItemChosenCallback {
                        sendNewTaskMessage(
                            ChatConversation.Message.makeMessage(
                                raw,
                                code,
                                getEditorLanguage(e.getData(CommonDataKeys.PSI_FILE)),
                                mapOf("dstLanguage" to it)
                            )
                        )
                    }.createPopup().showInBestPositionFor(editor)
            }
        }
    }

    companion object {
        val LANGUAGES = listOf(
            "C",
            "C++",
            "C#",
            "Go",
            "Java",
            "JavaScript",
            "Kotlin",
            "Lua",
            "Objective-C",
            "PHP",
            "Perl",
            "Python",
            "R",
            "Ruby",
            "Rust",
            "Swift",
            "TypeScript"
        )
    }
}