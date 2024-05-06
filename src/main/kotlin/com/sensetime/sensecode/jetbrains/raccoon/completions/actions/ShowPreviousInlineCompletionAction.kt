package com.sensetime.sensecode.jetbrains.raccoon.completions.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.CompletionPreview

internal class ShowPreviousInlineCompletionAction : BaseCodeInsightAction(false), DumbAware, InlineCompletionAction {
    override fun getHandler(): CodeInsightActionHandler =
        CodeInsightActionHandler { _, editor: Editor, _ ->
            CompletionPreview.getInstance(editor)?.showPreviousCompletion()
        }

    override fun isValidForLookup(): Boolean = true
}