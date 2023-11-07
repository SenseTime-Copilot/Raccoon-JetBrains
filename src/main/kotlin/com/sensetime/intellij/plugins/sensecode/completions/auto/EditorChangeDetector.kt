package com.sensetime.intellij.plugins.sensecode.completions.auto

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.sensetime.intellij.plugins.sensecode.topics.SenseCodeEditorChangedListener

class EditorChangeDetector : DocumentListener, EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
        SenseCodeEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        SenseCodeEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun mouseClicked(event: EditorMouseEvent) {
        SenseCodeEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun documentChanged(event: DocumentEvent) {
        for (editor in EditorFactory.getInstance().getEditors(event.document)) {
            SenseCodeEditorChangedListener.onEditorChanged(editor)
        }
    }
}