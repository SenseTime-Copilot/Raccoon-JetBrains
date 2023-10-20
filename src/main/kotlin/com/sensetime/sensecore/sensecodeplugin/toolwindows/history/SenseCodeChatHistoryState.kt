package com.sensetime.sensecore.sensecodeplugin.toolwindows.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.toolwindows.SenseCodeChatHistoryState",
    storages = [Storage("SenseCodeIntelliJChatHistory.xml")]
)
data class SenseCodeChatHistoryState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeChatHistoryState> {
    var historiesJsonString: String = "[]"
    var lastChatHistoryString: String = ChatHistory(ChatHistory.ChatType.FREE_CHAT).toJsonString()
    var lastTaskHistoryString: String = ChatHistory(ChatHistory.ChatType.CODE_TASK).toJsonString()

    override fun getState(): SenseCodeChatHistoryState {
        if (version != SenseCodePlugin.version) {
            version = SenseCodePlugin.version
        }
        return this
    }

    fun restore() {
        loadState(SenseCodeChatHistoryState(SenseCodePlugin.version))
    }

    override fun loadState(state: SenseCodeChatHistoryState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SenseCodeChatHistoryState
            get() = ApplicationManager.getApplication().getService(SenseCodeChatHistoryState::class.java)
    }
}