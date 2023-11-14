package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

object RaccoonPlugin {
    const val NAME: String = "Raccoon"
    const val PLUGIN_ID: String = "com.sensetime.sensecode.jetbrains.raccoon"

    val plugin: IdeaPluginDescriptor by lazy {
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: throw RuntimeException("Not found $PLUGIN_ID!")
    }

    val version: String
        get() = plugin.version
}