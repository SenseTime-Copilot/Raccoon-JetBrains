package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo


internal object RaccoonPlugin {
    private const val PLUGIN_ID: String = "com.sensetime.sensecode.jetbrains.raccoon"
    private fun getPlugin(): IdeaPluginDescriptor =
        requireNotNull(PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))) { "Not found $PLUGIN_ID!" }

    val name: String = getPlugin().name
    fun getVersion(): String = getPlugin().version

    val ideName: String = ApplicationInfo.getInstance().versionName.ifNullOrBlank("Unknown IDE")
    val ideInfo: String = ApplicationInfo.getInstance().run { "$versionName/$strictVersion ($apiVersion)" }
    val pluginInfo: String =
        RaccoonPlugin.run { "$name/${getVersion()} (${SystemInfo.getOsNameAndVersion()} ${SystemInfo.OS_ARCH})" }
    val userAgent: String = "${pluginInfo} ${ideInfo}"
}
