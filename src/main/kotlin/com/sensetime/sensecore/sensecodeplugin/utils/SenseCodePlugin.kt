package com.sensetime.sensecore.sensecodeplugin.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

object SenseCodePlugin {
    const val PLUGIN_ID: String = "com.sensetime.sensecore.sensecodeplugin"

    val plugin: IdeaPluginDescriptor by lazy {
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: throw RuntimeException("Not found $PLUGIN_ID!")
    }

    val version: String
        get() = plugin.version

    const val NAME: String = "SenseCode"
    const val TOOLWINDOW_ID: String = NAME
    const val STATUS_BAR_ID: String = NAME

    const val CODE_TASK_ACTIONS_GROUP = "com.sensetime.sensecore.sensecodeplugin.actions.CodeTaskGroup"
}