package com.sensetime.intellij.plugins.sensecode.completions.preview.render

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

class InlineElementRenderer(private val text: String) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return inlay.editor.let { it.contentComponent.getFontMetrics(GraphicsUtils.getFont(it)).stringWidth(text) }
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        if (text.isNotEmpty()) {
            val editor = inlay.editor
            g.color = GraphicsUtils.niceContrastColor
            g.font = GraphicsUtils.getFont(editor)
            g.drawString(text, targetRegion.x, targetRegion.y + editor.ascent)
        }
    }
}