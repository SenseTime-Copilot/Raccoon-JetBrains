package com.sensetime.sensecode.jetbrains.raccoon.resources

import com.intellij.openapi.util.IconLoader

object RaccoonIcons {
    private const val ICONS_ROOT_DIR = "/icons"
    private const val USERS_DIR = "$ICONS_ROOT_DIR/users"
    private const val STATUS_BAR_DIR = "$ICONS_ROOT_DIR/statusBar"
    private const val TOOLWINDOW_DIR = "$ICONS_ROOT_DIR/toolWindow"

    @JvmField
    val AUTHENTICATED_USER = IconLoader.getIcon("$USERS_DIR/authenticatedUser", RaccoonIcons::class.java)

    @JvmField
    val AUTHENTICATED_USER_BIG = IconLoader.getIcon("$USERS_DIR/authenticatedUserBig", RaccoonIcons::class.java)

    @JvmField
    val UNAUTHENTICATED_USER = IconLoader.getIcon("$USERS_DIR/unauthenticatedUser", RaccoonIcons::class.java)

    @JvmField
    val UNAUTHENTICATED_USER_BIG = IconLoader.getIcon("$USERS_DIR/unauthenticatedUserBig", RaccoonIcons::class.java)

    @JvmField
    val STATUS_BAR_DEFAULT = IconLoader.getIcon("$STATUS_BAR_DIR/default", RaccoonIcons::class.java)

    @JvmField
    val STATUS_BAR_SUCCESS = IconLoader.getIcon("$STATUS_BAR_DIR/success", RaccoonIcons::class.java)

    @JvmField
    val STATUS_BAR_EMPTY = IconLoader.getIcon("$STATUS_BAR_DIR/empty", RaccoonIcons::class.java)

    @JvmField
    val STATUS_BAR_ERROR = IconLoader.getIcon("$STATUS_BAR_DIR/error", RaccoonIcons::class.java)

    @JvmField
    val TOOLWINDOW_USER = IconLoader.getIcon("$TOOLWINDOW_DIR/user", RaccoonIcons::class.java)

    @JvmField
    val TOOLWINDOW_ASSISTANT = IconLoader.getIcon("$TOOLWINDOW_DIR/assistant", RaccoonIcons::class.java)

    @JvmField
    val TOOLWINDOW_SUBMIT = IconLoader.getIcon("$TOOLWINDOW_DIR/submit", RaccoonIcons::class.java)

    @JvmField
    val TOOLWINDOW_STOP = IconLoader.getIcon("$TOOLWINDOW_DIR/stop", RaccoonIcons::class.java)

    @JvmField
    val TOOLWINDOW_RACCOON = IconLoader.getIcon("$TOOLWINDOW_DIR/Raccoon.svg", RaccoonIcons::class.java)
}