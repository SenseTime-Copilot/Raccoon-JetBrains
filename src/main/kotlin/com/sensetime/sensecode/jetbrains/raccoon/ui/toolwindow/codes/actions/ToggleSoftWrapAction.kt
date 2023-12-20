package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle

class ToggleSoftWrapAction(private val editor: EditorEx) : AnAction(
    RaccoonBundle.message("codes.actions.softWrap.name"),
    RaccoonBundle.message("codes.actions.softWrap.description"),
    AllIcons.Actions.ToggleSoftWrap
) {
    override fun actionPerformed(e: AnActionEvent) {
        editor.settings.apply {
            if (isUseSoftWraps) {
                isUseSoftWraps = false
                editor.setHorizontalScrollbarVisible(true)
            } else {
                isUseSoftWraps = true
                editor.setHorizontalScrollbarVisible(false)
            }
        }
    }
}