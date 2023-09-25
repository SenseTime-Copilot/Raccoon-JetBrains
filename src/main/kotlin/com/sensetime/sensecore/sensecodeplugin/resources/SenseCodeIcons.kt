package com.sensetime.sensecore.sensecodeplugin.resources

import com.intellij.openapi.util.IconLoader

object SenseCodeIcons {
    private const val ICONS_ROOT_DIR = "/icons"

    @JvmField
    val DEFAULT_USER_AVATAR = IconLoader.getIcon("$ICONS_ROOT_DIR/users/defaultUserAvatar", SenseCodeIcons::class.java)
}