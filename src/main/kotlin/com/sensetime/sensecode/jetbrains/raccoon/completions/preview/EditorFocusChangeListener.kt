package com.sensetime.sensecode.jetbrains.raccoon.completions.preview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener

class EditorFocusChangeListener : FocusChangeListener {
    override fun focusLost(editor: Editor) {
        CompletionPreview.getInstance(editor)?.cancel()
    }
}