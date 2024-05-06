package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultCompactActionGroup
import com.intellij.openapi.editor.ex.EditorEx
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions.CopyToClipboardAction
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions.InsertedAtCursorAction
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions.ToggleSoftWrapAction

internal class CodeActionsToolbarBuilder {
    fun build(editor: EditorEx, language: String): ActionToolbar = DefaultCompactActionGroup().apply {
        add(ToggleSoftWrapAction(editor))
        add(CopyToClipboardAction(editor, language))
        add(InsertedAtCursorAction(editor, language))
    }.let {
        ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, it, true)
    }.apply {
        targetComponent = editor.component
    }
}