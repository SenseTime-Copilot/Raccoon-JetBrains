package com.sensetime.sensecode.jetbrains.raccoon.tasks

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCodeChunk
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.toVariableExpression
import javax.swing.ListSelectionModel


internal class CodeConversion : CodeTaskActionBase() {
    override fun sendNewTaskMessage(
        project: Project,
        editor: Editor,
        code: String,
        language: String,
        localKnowledge: List<LLMCodeChunk>?
    ) {
        JBPopupFactory.getInstance().createPopupChooserBuilder(LANGUAGES)
            .setVisibleRowCount(7)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setItemChosenCallback {
                sendNewTaskMessage(
                    project,
                    code,
                    language,
                    mapOf(DST_LANGUAGE to it),
                    localKnowledge
                )
            }
            .createPopup().showInBestPositionFor(editor)
    }

    companion object {
        const val DST_LANGUAGE = "dstLanguage"
        val dstLanguageExpression: String = DST_LANGUAGE.toVariableExpression()

        private val LANGUAGES = listOf(
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