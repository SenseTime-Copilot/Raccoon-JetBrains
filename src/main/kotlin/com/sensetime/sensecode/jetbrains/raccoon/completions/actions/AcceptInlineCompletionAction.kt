package com.sensetime.sensecode.jetbrains.raccoon.completions.actions

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.CompletionPreview

object AcceptInlineCompletionAction : EditorAction(AcceptInlineCompletionHandler()), HintManagerImpl.ActionToIgnore,
    InlineCompletionAction {
    class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            CompletionPreview.getInstance(editor)?.apply()
        }

        override fun isEnabledForCaret(
            editor: Editor,
            caret: Caret,
            dataContext: DataContext
        ): Boolean {
            return CompletionPreview.getInstance(editor)?.done ?: false
        }
    }
}