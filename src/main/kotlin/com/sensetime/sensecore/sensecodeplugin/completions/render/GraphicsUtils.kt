package com.sensetime.sensecore.sensecodeplugin.completions.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import kotlin.math.sqrt

object GraphicsUtils {
    fun getFont(editor: Editor): Font {
        val editorFont = editor.colorsScheme.getFont(EditorFontType.ITALIC)
        return completionFont?.let {
            Font(it.name, editorFont.style or Font.ITALIC, editorFont.size)
        } ?: editorFont
    }

    private val completionFont: Font? =
        GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.firstOrNull { -1 == it.canDisplayUpTo("中文") }

    val niceContrastColor: Color = JBColor.GRAY
//    by lazy {
//        val averageBrightness = (getBrightness(JBColor.background()) + getBrightness(JBColor.foreground())) / 2.0
//        var currentResult: Color = JBColor.LIGHT_GRAY
//        var bestResult = currentResult
//        var distance = Double.MAX_VALUE
//        var currentBrightness = getBrightness(currentResult)
//        val minBrightness = getBrightness(JBColor.DARK_GRAY)
//
//        while (currentBrightness > minBrightness) {
//            if (abs(currentBrightness - averageBrightness) < distance) {
//                distance = abs(currentBrightness - averageBrightness)
//                bestResult = currentResult
//            }
//            currentResult = currentResult.darker()
//            currentBrightness = getBrightness(currentResult)
//        }
//        bestResult
//    }

    private fun getBrightness(color: Color): Double {
        return sqrt(
            (color.red * color.red * 0.241) + (color.green * color.green * 0.691) + (color.blue * color.blue * 0.068)
        )
    }
}