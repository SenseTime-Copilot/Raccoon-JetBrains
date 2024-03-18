package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.notification.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.LoginDialog
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank

object RaccoonNotification {
    const val GROUP_ID: String = "Raccoon Notification Group"

    @JvmStatic
    private val notificationGroup: NotificationGroup
        get() = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)

    @JvmStatic
    fun notifySettingsAction(notification: Notification, actionName: String) {
        notification.addAction(NotificationAction.createSimple(actionName) {
            RaccoonUIUtils.showRaccoonSettings()
        }).notify(null)
    }

    private var isNotifiedGotoLogin: Boolean = false

    @JvmStatic
    fun notifyGotoLogin(once: Boolean) {
        if (once && isNotifiedGotoLogin) {
            return
        }
        isNotifiedGotoLogin = true
        notificationGroup.createNotification(
            RaccoonBundle.message("notification.settings.login.notloggedin"),
            "",
            NotificationType.WARNING
        ).addAction(NotificationAction.createSimple(RaccoonBundle.message("notification.settings.goto.login")) {
            LoginDialog(null, null).showAndGet()
        }).notify(null)
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
            RaccoonBundle.message("completions.inline.warning.noCompletionSuggestion"),
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
                        RaccoonBundle.message("notification.editor.selectedText.tooLong"),
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