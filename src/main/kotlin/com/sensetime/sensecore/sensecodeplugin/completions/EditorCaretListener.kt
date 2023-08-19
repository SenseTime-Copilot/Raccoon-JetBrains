package com.sensetime.sensecore.sensecodeplugin.completions

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener

class EditorCaretListener : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
        CompletionPreview.getInstance(event.editor)?.cancel()
    }
}