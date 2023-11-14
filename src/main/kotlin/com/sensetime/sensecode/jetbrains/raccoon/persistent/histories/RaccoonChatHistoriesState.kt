package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin

@State(
    name = "com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.RaccoonChatHistoriesState",
    storages = [Storage("RaccoonJetBrainsChatHistories.xml")]
)
data class RaccoonChatHistoriesState(
    var version: String = ""
) : PersistentStateComponent<RaccoonChatHistoriesState> {
    var historiesJsonString: String = "[]"
    var lastChatHistoryString: String = "{}"

    fun restore() {
        loadState(RaccoonChatHistoriesState(RaccoonPlugin.version))
    }

    override fun getState(): RaccoonChatHistoriesState {
        version = RaccoonPlugin.version
        return this
    }

    override fun loadState(state: RaccoonChatHistoriesState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: RaccoonChatHistoriesState
            get() = ApplicationManager.getApplication().getService(RaccoonChatHistoriesState::class.java)
    }
}

