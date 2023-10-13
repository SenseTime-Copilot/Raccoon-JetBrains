package com.sensetime.sensecore.sensecodeplugin.toolwindows.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.*
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.toolwindows.SenseCodeChatHistoryState",
    storages = [Storage("SenseCodeIntelliJChatHistory.xml")]
)
data class SenseCodeChatHistoryState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeChatHistoryState> {
    var historiesJsonString: String = "[]"
    var histories: List<ChatHistory>
        get() = historiesJsonString.toChatHistories()
        set(value) {
            historiesJsonString = value.toJsonString()
        }

    var lastFreeChatConversationsString: String = "[]"
    var lastFreeChatConversations: List<ChatConversation>
        get() = lastFreeChatConversationsString.toChatConversations()
        set(value) {
            lastFreeChatConversationsString = value.toJsonString()
        }

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