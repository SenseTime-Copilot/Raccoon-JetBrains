package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener

class EditorChangeDetector : DocumentListener, EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
        RaccoonEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        RaccoonEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun mouseClicked(event: EditorMouseEvent) {
        RaccoonEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun documentChanged(event: DocumentEvent) {
        for (editor in EditorFactory.getInstance().getEditors(event.document)) {
            RaccoonEditorChangedListener.onEditorChanged(editor)
        }
    }
}