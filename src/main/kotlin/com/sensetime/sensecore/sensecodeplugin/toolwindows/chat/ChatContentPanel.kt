package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.SenseCodeChatHistoryState
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toJsonString

class ChatContentPanel : ContentPanelBase() {
    override val type: ChatHistory.ChatType = ChatHistory.ChatType.FREE_CHAT
    override var lastHistoryState: ChatHistory
        get() = SenseCodeChatHistoryState.instance.lastChatHistoryString.toChatHistory()
        set(value) {
            SenseCodeChatHistoryState.instance.lastChatHistoryString = value.toJsonString()
        }
}