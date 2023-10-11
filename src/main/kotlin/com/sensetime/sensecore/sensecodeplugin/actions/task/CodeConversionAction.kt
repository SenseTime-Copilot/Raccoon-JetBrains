package com.sensetime.sensecore.sensecodeplugin.actions.task

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_TASKS_TOPIC
import javax.swing.ListSelectionModel

class CodeConversionAction : CodeTaskActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        e.getData(CommonDataKeys.EDITOR)?.let { editor ->
            getEditorSelectedText(editor)?.let { code ->
                promptTemplate?.let { promptTemplate ->
                    JBPopupFactory.getInstance().createPopupChooserBuilder(LANGUAGES)
                        .setVisibleRowCount(7)
                        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                        .setItemChosenCallback {
                            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_TASKS_TOPIC)
                                .onNewTask(
                                    promptTemplate.displayText.format(it, code),
                                    promptTemplate.prompt?.format(it, code)
                                )
                        }.createPopup().showInBestPositionFor(editor)
                }
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