package com.sensetime.sensecode.jetbrains.raccoon.completions.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.CompletionPreview

internal class EscapeEditorActionHandler(private val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        CompletionPreview.getInstance(editor)?.cancel()
        if (myOriginalHandler.isEnabled(editor, caret, dataContext)) {
            myOriginalHandler.execute(editor, caret, dataContext)
        }
    }

    override fun isEnabledForCaret(
        editor: Editor,
        caret: Caret,
        dataContext: DataContext
    ): Boolean {
        return (null != CompletionPreview.getInstance(editor)) || myOriginalHandler.isEnabled(
            editor,
            caret,
            dataContext
        )
    }
}