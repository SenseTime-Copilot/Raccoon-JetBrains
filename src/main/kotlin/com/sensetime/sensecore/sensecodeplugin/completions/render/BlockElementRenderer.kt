package com.sensetime.sensecore.sensecodeplugin.completions.render

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

class BlockElementRenderer(private val blockText: List<String>?) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return blockText?.let { lines ->
            val metrics = inlay.editor.let { it.contentComponent.getFontMetrics(GraphicsUtils.getFont(it)) }
            lines.maxOfOrNull { metrics.stringWidth(it) }
        } ?: 0
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        return inlay.editor.lineHeight * (blockText?.size ?: 0)
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        blockText?.let { lines ->
            val editor = inlay.editor
            g.color = GraphicsUtils.niceContrastColor
            g.font = GraphicsUtils.getFont(editor)
            lines.forEachIndexed { i, line ->
                g.drawString(line, 0, targetRegion.y + i * editor.lineHeight + editor.ascent)
            }
        }
    }
}