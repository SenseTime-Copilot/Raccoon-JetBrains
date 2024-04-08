package com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import java.awt.Color
import java.awt.Font
import kotlin.math.sqrt

internal object GraphicsUtils {
    fun getFont(editor: Editor): Font = editor.colorsScheme.getFont(EditorFontType.ITALIC)
        .let { font -> UIUtil.getFontWithFallback(if (font.isItalic) font else font.deriveFont(font.style or Font.ITALIC)) }

    val niceContrastColor: Color
        get() = kotlin.runCatching {
            RaccoonSettingsState.instance.inlineCompletionColor.letIfNotBlank {
                ColorUtil.fromHex(
                    it
                )
            }
        }.getOrNull() ?: Color(102, 109, 117)

    private fun getBrightness(color: Color): Double {
        return sqrt(
            (color.red * color.red * 0.241) + (color.green * color.green * 0.691) + (color.blue * color.blue * 0.068)
        )
    }
}