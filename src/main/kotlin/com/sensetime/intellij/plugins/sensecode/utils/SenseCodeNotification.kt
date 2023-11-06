package com.sensetime.intellij.plugins.sensecode.utils

import com.intellij.notification.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.ui.SenseCodeConfigurable

object SenseCodeNotification {
    const val GROUP_ID: String = "SenseCode Notification Group"

    @JvmStatic
    private val notificationGroup: NotificationGroup
        get() = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)

    @JvmStatic
    fun notifySettingsAction(notification: Notification, actionName: String) {
        notification.addAction(NotificationAction.createSimple(actionName) {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, SenseCodeConfigurable::class.java)
        }).notify(null)
    }

    @JvmStatic
    fun notifyLoginWithSettingsAction() {
        notifySettingsAction(
            notificationGroup.createNotification(
                SenseCodeBundle.message("notification.settings.login.notloggedin"),
                "",
                NotificationType.WARNING
            ), SenseCodeBundle.message("notification.settings.goto.login")
        )
    }

    private var lastPopupMessage: String = ""

    @JvmStatic
    fun popupMessageInBestPositionForEditor(message: String, editor: Editor?, diffOnly: Boolean) {
        editor?.takeUnless { diffOnly && (message == lastPopupMessage) }?.let {
            JBPopupFactory.getInstance().createMessage(message).showInBestPositionFor(it)
        }
        lastPopupMessage = message
    }

    @JvmStatic
    fun popupNoCompletionSuggestionMessage(editor: Editor?, diffOnly: Boolean) {
        popupMessageInBestPositionForEditor(
            SenseCodeBundle.message("completions.inline.warning.noCompletionSuggestion"),
            editor,
            diffOnly
        )
    }

    @JvmStatic
    fun checkEditorSelectedText(maxInputTokens: Int, editor: Editor?, diffOnly: Boolean): String? =
        editor?.let {
            it.selectionModel.selectedText?.letIfNotBlank { text ->
                if (text.length / 4 > maxInputTokens) {
                    popupMessageInBestPositionForEditor(
                        SenseCodeBundle.message("notification.editor.selectedText.tooLong"),
                        editor,
                        diffOnly
                    )
                    null
                } else {
                    text
                }
            }
        }
}