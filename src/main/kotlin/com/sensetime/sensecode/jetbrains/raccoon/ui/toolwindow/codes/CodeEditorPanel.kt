package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.getFileTypeOrDefault
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CodeEditorPanel(
    project: Project?, code: String,
    private val languagePair: Pair<String, RaccoonLanguages.Language>?,
    isOneLineMode: Boolean = false
) : EditorTextField(
    createDocumentFromCode(code, languagePair),
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
            setHorizontalScrollbarVisible(true)
            colorsScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
        }
    }

    companion object {
        private fun createDocumentFromCode(
            code: String,
            languagePair: Pair<String, RaccoonLanguages.Language>?
        ): Document = StringUtil.convertLineSeparators(code).let { convertedCode ->
            FileDocumentManager.getInstance().getDocument(
                LightVirtualFile(
                    "${PathManager.getTempPath()}/raccoon_tmp_${
                        DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
                    }${languagePair?.second?.primaryExtension.ifNullOrBlank()}", convertedCode
                )
            ) ?: EditorFactory.getInstance().createDocument(convertedCode)
        }
    }
}