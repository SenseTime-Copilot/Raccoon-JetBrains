package com.sensetime.sensecode.jetbrains.raccoon.completions.preview

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener

class EditorCaretListener : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
        CompletionPreview.getInstance(event.editor)?.cancel()
    }
}