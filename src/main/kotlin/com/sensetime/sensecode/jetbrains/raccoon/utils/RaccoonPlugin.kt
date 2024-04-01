package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId


internal object RaccoonPlugin {
    private const val PLUGIN_ID: String = "com.sensetime.sensecode.jetbrains.raccoon"
    private fun getPlugin(): IdeaPluginDescriptor =
        requireNotNull(PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))) { "Not found $PLUGIN_ID!" }

    val name: String = getPlugin().name
    val ideName: String = ApplicationInfo.getInstance().versionName.ifNullOrBlank("Unknown IDE")
    fun getVersion(): String = getPlugin().version
}