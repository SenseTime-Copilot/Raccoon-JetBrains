package com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import java.awt.Point
import java.awt.Rectangle

class CompletionInlays(parent: Disposable) : Disposable {
    private var inlineInlay: Inlay<*>? = null
    private var blockInlay: Inlay<*>? = null

    init {
        Disposer.register(parent, this)
    }

    val isEmpty: Boolean
        get() = (null == inlineInlay) && (null == blockInlay)
    private val bounds: Rectangle
        get() = (inlineInlay?.bounds ?: Rectangle()).union(blockInlay?.bounds ?: Rectangle())

    fun contains(point: Point): Boolean = bounds.contains(point)

    fun clear() {
        inlineInlay?.let { Disposer.dispose(it) }
        inlineInlay = null

        blockInlay?.let { Disposer.dispose(it) }
        blockInlay = null
    }

    override fun dispose() {
        clear()
    }

    fun render(editor: Editor, completion: String?, offset: Int) {
        clear()

        val lines = completion?.lines()
        if (lines.isNullOrEmpty()) {
            return
        }

        val firstLine = lines.first()
        if (firstLine.isNotEmpty()) {
            inlineInlay = editor.inlayModel.addInlineElement(offset, true, InlineElementRenderer(lines.first()))
        }
        if (lines.size > 1) {
            blockInlay = editor.inlayModel.addBlockElement(
                offset,
                true,
                false,
                1,
                BlockElementRenderer(lines.subList(1, lines.size))
            )
        }
    }
}