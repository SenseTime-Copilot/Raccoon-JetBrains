package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.SenseCodeChatHistoryState
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toJsonString

class TaskContentPanel : ContentPanelBase() {
    override val type: ChatHistory.ChatType = ChatHistory.ChatType.CODE_TASK
    override var lastHistoryState: ChatHistory
        get() = SenseCodeChatHistoryState.instance.lastTaskHistoryString.toChatHistory()
        set(value) {
            SenseCodeChatHistoryState.instance.lastTaskHistoryString = value.toJsonString()
        }

    fun newTask(type: String, userMessage: ChatConversation.Message?) {
        makePromptConversation(type, userMessage)?.let {
            conversationListPanel.conversationListModel.add(it)
            startGenerate()
        }
    }
}