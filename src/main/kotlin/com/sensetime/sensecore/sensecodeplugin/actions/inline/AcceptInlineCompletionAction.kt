package com.sensetime.sensecore.sensecodeplugin.actions.inline

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.sensetime.sensecore.sensecodeplugin.completions.CompletionPreview

object AcceptInlineCompletionAction : EditorAction(AcceptInlineCompletionHandler()), ActionToIgnore,
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