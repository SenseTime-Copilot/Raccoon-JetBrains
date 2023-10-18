package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.SenseCodeChatHistoryState
import kotlin.reflect.KMutableProperty0

class TaskContentPanel() : ContentPanelBase() {
    override val type: ChatHistory.ChatType = ChatHistory.ChatType.CODE_TASK
    override val lastStateProp: KMutableProperty0<ChatHistory> = SenseCodeChatHistoryState.instance::lastTaskHistory

    fun newTask(type: String, userMessage: ChatConversation.Message?) {
        makePromptConversation(type, userMessage)?.let {
            conversationListPanel.conversationListModel.add(it)
            startGenerate()
        }
    }
}