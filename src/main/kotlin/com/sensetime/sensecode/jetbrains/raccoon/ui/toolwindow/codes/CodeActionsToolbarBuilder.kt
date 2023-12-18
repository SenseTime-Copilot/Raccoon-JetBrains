package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultCompactActionGroup
import com.intellij.openapi.editor.Editor
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions.CopyToClipboardAction
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions.InsertedAtCursorAction

class CodeActionsToolbarBuilder {
    fun build(editor: Editor): ActionToolbar = DefaultCompactActionGroup().apply {
        add(CopyToClipboardAction(editor))
        add(InsertedAtCursorAction(editor))
    }.let {
        ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, it, true)
    }.apply {
        targetComponent = editor.component
    }
}