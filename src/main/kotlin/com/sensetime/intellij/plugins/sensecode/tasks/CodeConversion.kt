package com.sensetime.intellij.plugins.sensecode.tasks

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import javax.swing.ListSelectionModel

class CodeConversion : CodeTaskActionBase() {
    override fun sendNewTaskMessage(editor: Editor, code: String, language: String) {
        JBPopupFactory.getInstance().createPopupChooserBuilder(LANGUAGES)
            .setVisibleRowCount(7)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setItemChosenCallback { sendNewTaskMessage(code, language, mapOf(DST_LANGUAGE to it)) }
            .createPopup().showInBestPositionFor(editor)
    }

    companion object {
        const val DST_LANGUAGE = "dstLanguage"
        val dstLanguageExpression: String = ModelConfig.DisplayTextTemplate.toArgExpression(DST_LANGUAGE)

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