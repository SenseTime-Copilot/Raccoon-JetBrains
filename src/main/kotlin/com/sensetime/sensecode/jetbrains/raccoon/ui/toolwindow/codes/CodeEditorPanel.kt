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
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.getFileTypeOrDefault
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class CodeEditorPanel(
    project: Project?, code: String,
    val languagePair: Pair<String, RaccoonLanguages.Language>?,
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
            val borderColor = ColorUtil.fromHex("#505050")
            permanentHeaderComponent = CodeHeaderPanel(this, languagePair).also {
                it.border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(borderColor, 1, 1, 0, 1),
                    JBUI.Borders.empty(0, RaccoonUIUtils.BIG_GAP_SIZE, 0, RaccoonUIUtils.SMALL_GAP_SIZE)
                )
            }
            headerComponent = null
            settings.isUseSoftWraps = true
            colorsScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
            backgroundColor = colorsScheme.defaultBackground
            setBorder(JBUI.Borders.customLine(borderColor, 1))
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
                    }${languagePair?.second?.primaryExtension.ifNullOrBlank("")}", convertedCode
                )
            ) ?: EditorFactory.getInstance().createDocument(convertedCode)
        }
    }
}