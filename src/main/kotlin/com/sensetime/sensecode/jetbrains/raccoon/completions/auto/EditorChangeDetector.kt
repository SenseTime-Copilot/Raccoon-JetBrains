package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener

internal class EditorChangeDetector : DocumentListener, EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
        event.editor.project?.let {
            RaccoonEditorChangedListener.onEditorChanged(
                it,
                RaccoonEditorChangedListener.Type.MOUSE_PRESSED,
                event.editor
            )
        }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        event.editor.project?.let {
            RaccoonEditorChangedListener.onEditorChanged(
                it,
                RaccoonEditorChangedListener.Type.MOUSE_RELEASED,
                event.editor
            )
        }
    }

    override fun mouseClicked(event: EditorMouseEvent) {
        event.editor.project?.let {
            RaccoonEditorChangedListener.onEditorChanged(
                it,
                RaccoonEditorChangedListener.Type.MOUSE_CLICKED,
                event.editor
            )
        }

    }

    override fun documentChanged(event: DocumentEvent) {
        for (editor in EditorFactory.getInstance().getEditors(event.document)) {
            editor.project?.let {
                RaccoonEditorChangedListener.onEditorChanged(
                    it,
                    RaccoonEditorChangedListener.Type.DOCUMENT_CHANGED,
                    editor
                )
            }
        }
    }
}