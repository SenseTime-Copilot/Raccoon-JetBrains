package com.sensetime.sensecore.sensecodeplugin.resources

import com.intellij.openapi.util.IconLoader

object SenseCodeIcons {
    private const val ICONS_ROOT_DIR = "/icons"
    private const val USERS_DIR = "$ICONS_ROOT_DIR/users"
    private const val STATUS_BAR_DIR = "$ICONS_ROOT_DIR/statusBar"
    private const val TOOLWINDOW_DIR = "$ICONS_ROOT_DIR/toolWindow"

    @JvmField
    val LOGGED_USER = IconLoader.getIcon("$USERS_DIR/loggedUser", SenseCodeIcons::class.java)

    @JvmField
    val NOT_LOGGED_USER = IconLoader.getIcon("$USERS_DIR/notLoggedUser", SenseCodeIcons::class.java)

    @JvmField
    val STATUS_BAR_DEFAULT = IconLoader.getIcon("$STATUS_BAR_DIR/default", SenseCodeIcons::class.java)

    @JvmField
    val STATUS_BAR_SUCCESS = IconLoader.getIcon("$STATUS_BAR_DIR/success", SenseCodeIcons::class.java)

    @JvmField
    val STATUS_BAR_ERROR = IconLoader.getIcon("$STATUS_BAR_DIR/error", SenseCodeIcons::class.java)

    @JvmField
    val TOOLWINDOW_USER = IconLoader.getIcon("$TOOLWINDOW_DIR/user", SenseCodeIcons::class.java)

    @JvmField
    val TOOLWINDOW_ASSISTANT = IconLoader.getIcon("$TOOLWINDOW_DIR/assistant", SenseCodeIcons::class.java)

    @JvmField
    val TOOLWINDOW_SUBMIT = IconLoader.getIcon("$TOOLWINDOW_DIR/submit", SenseCodeIcons::class.java)

    @JvmField
    val TOOLWINDOW_STOP = IconLoader.getIcon("$TOOLWINDOW_DIR/stop", SenseCodeIcons::class.java)

    @JvmField
    val TOOLWINDOW_SENSECODE = IconLoader.getIcon("$TOOLWINDOW_DIR/SenseCode", SenseCodeIcons::class.java)
}