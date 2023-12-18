package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes

import com.intellij.openapi.editor.Editor
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel

class CodeHeaderPanel(
    editor: Editor, languagePair: Pair<String, RaccoonLanguages.Language>?
) : JPanel(BorderLayout()) {
    init {
        add(JLabel(languagePair?.first).apply {
            languagePair?.second?.color?.letIfNotBlank { colorHexString ->
                kotlin.runCatching {
                    Color.decode(colorHexString)
                }.onSuccess { foreground = it }
            }
        }, BorderLayout.LINE_START)
        add(CodeActionsToolbarBuilder().build(editor).component, BorderLayout.LINE_END)
    }
}