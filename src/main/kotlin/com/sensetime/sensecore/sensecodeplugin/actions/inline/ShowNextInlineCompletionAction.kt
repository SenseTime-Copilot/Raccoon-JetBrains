package com.sensetime.sensecore.sensecodeplugin.actions.inline

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.sensetime.sensecore.sensecodeplugin.completions.CompletionPreview

class ShowNextInlineCompletionAction : BaseCodeInsightAction(false), DumbAware, InlineCompletionAction {
    override fun getHandler(): CodeInsightActionHandler =
        CodeInsightActionHandler { _, editor: Editor, _ ->
            CompletionPreview.getInstance(editor)?.showNextCompletion()
        }

    override fun isValidForLookup(): Boolean = true
}