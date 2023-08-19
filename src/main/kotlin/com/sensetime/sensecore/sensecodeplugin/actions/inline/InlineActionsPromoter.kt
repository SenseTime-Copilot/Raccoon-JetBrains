package com.sensetime.sensecore.sensecodeplugin.actions.inline

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.sensetime.sensecore.sensecodeplugin.completions.CompletionPreview

class InlineActionsPromoter : ActionPromoter {
    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {
        return CommonDataKeys.EDITOR.getData(context)?.let { editor ->
            CompletionPreview.getInstance(editor)?.let {
                actions.filter { action -> action is InlineCompletionAction }
            }
        }
    }
}
