package com.sensetime.intellij.plugins.sensecode.persistent.histories

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin

@State(
    name = "com.sensetime.intellij.plugins.sensecode.persistent.histories.SenseCodeChatHistoriesState",
    storages = [Storage("SenseCodeIntelliJChatHistories.xml")]
)
data class SenseCodeChatHistoriesState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeChatHistoriesState> {
    var historiesJsonString: String = "[]"
    var lastChatHistoryString: String = "{}"

    fun restore() {
        loadState(SenseCodeChatHistoriesState(SenseCodePlugin.version))
    }

    override fun getState(): SenseCodeChatHistoriesState {
        version = SenseCodePlugin.version
        return this
    }

    override fun loadState(state: SenseCodeChatHistoriesState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SenseCodeChatHistoriesState
            get() = ApplicationManager.getApplication().getService(SenseCodeChatHistoriesState::class.java)
    }
}

