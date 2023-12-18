package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.getFileTypeOrDefault

class CodeEditorPanel(
    project: Project?, code: String,
    private val languagePair: Pair<String, RaccoonLanguages.Language>?,
    isOneLineMode: Boolean = false
) : EditorTextField(
    EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(code)),
    project, languagePair?.second.getFileTypeOrDefault(), true, isOneLineMode
) {
    private val editorEx: EditorEx?
        get() = getEditor(false)

    fun updateHorizontalScrollbarVisible(isVisible: Boolean) {
        editorEx?.setHorizontalScrollbarVisible(isVisible)
    }

    fun updateIsOneLineMode(newMode: Boolean) {
        if (isOneLineMode != newMode) {
            isOneLineMode = newMode
        }
    }

    init {
        border = JBUI.Borders.empty(RaccoonUIUtils.DEFAULT_GAP_SIZE, 0)
    }

    override fun createEditor(): EditorEx {
        return super.createEditor().apply {
            permanentHeaderComponent = CodeHeaderPanel(this, languagePair)
            headerComponent = null
        }
    }
}