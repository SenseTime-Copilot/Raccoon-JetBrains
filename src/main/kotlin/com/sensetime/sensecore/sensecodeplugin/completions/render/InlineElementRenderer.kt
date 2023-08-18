package com.sensetime.sensecore.sensecodeplugin.completions.render

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

class InlineElementRenderer(private val text: String?) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return text?.let { line ->
            inlay.editor.let { it.contentComponent.getFontMetrics(GraphicsUtils.getFont(it)).stringWidth(line) }
        } ?: 0
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        text?.let {
            val editor = inlay.editor
            g.color = GraphicsUtils.niceContrastColor
            g.font = GraphicsUtils.getFont(editor)
            g.drawString(it, targetRegion.x, targetRegion.y + editor.ascent)
        }
    }
}