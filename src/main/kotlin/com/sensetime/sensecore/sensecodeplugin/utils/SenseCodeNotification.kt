package com.sensetime.sensecore.sensecodeplugin.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeConfigurable

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
}