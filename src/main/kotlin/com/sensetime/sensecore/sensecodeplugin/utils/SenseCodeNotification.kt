package com.sensetime.sensecore.sensecodeplugin.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeConfigurable
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState

object SenseCodeNotification {
    @JvmStatic
    fun getBalloonGroup(): NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("com.sensetime.sensecore.sensecodeplugin.balloon")

    @JvmStatic
    fun notifySettingsAction(notification: Notification, actionName: String) {
        notification.addAction(NotificationAction.createSimple(actionName) {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, SenseCodeConfigurable::class.java)
        }).notify(null)
    }

    @JvmStatic
    fun notifyLoginWithSettingsAction() {
        notifySettingsAction(
            getBalloonGroup().createNotification(
                SenseCodeBundle.message("notification.settings.login.notloggedin"),
                "",
                NotificationType.WARNING
            ), SenseCodeBundle.message("notification.settings.goto.login")
        )
    }

    private var lastErrorMessage: String = ""

    @JvmStatic
    fun popupMessageInBestPositionForEditor(message: String, editor: Editor?) {
        if (!SenseCodeSettingsState.instance.isAutoCompleteMode || (message != lastErrorMessage)) {
            editor?.let {
                JBPopupFactory.getInstance().createMessage(message).showInBestPositionFor(it)
            }
        }
        lastErrorMessage = message
    }

    @JvmStatic
    fun popupNoCompletionSuggestionMessage(editor: Editor?) {
        if (!SenseCodeSettingsState.instance.isAutoCompleteMode) {
            popupMessageInBestPositionForEditor(
                SenseCodeBundle.message("completions.inline.warning.noCompletionSuggestion"),
                editor
            )
        }
    }

    @JvmStatic
    fun checkEditorSelectedText(maxInputTokens: Int, editor: Editor?): String? = editor?.let {
        it.selectionModel.selectedText?.takeIf { text -> text.isNotBlank() }?.let { text ->
            if (text.length / 4 > maxInputTokens) {
                popupMessageInBestPositionForEditor(
                    SenseCodeBundle.message("notification.editor.selectedText.tooLong"),
                    editor
                )
                null
            } else {
                text
            }
        }
    }
}