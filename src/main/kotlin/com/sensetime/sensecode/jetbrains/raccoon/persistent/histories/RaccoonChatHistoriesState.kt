package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils.EMPTY_JSON_OBJECT_STRING


@State(
    name = "com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.RaccoonChatHistoriesState",
    storages = [Storage("RaccoonJetBrainsChatHistories.xml")]
)
data class RaccoonChatHistoriesState(
    var version: String = ""
) : PersistentStateComponent<RaccoonChatHistoriesState> {
    var historiesJsonString: String = "[]"
    var lastChatHistoryString: String = EMPTY_JSON_OBJECT_STRING

    fun restore() {
        loadState(RaccoonChatHistoriesState(RaccoonPlugin.getVersion()))
    }

    override fun getState(): RaccoonChatHistoriesState {
        version = RaccoonPlugin.getVersion()
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
