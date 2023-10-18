package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.SenseCodeChatHistoryState
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import java.awt.event.ActionEvent
import javax.swing.JButton
import kotlin.reflect.KMutableProperty0

class ChatContentPanel() : ContentPanelBase() {
    override val type: ChatHistory.ChatType = ChatHistory.ChatType.FREE_CHAT
    override val lastStateProp: KMutableProperty0<ChatHistory> = SenseCodeChatHistoryState.instance::lastChatHistory

    override val newChatButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.newChat"))
            .apply { addActionListener(this@ChatContentPanel::onNewChat) }

    override fun dispose() {
        super.dispose()
        newChatButton.removeActionListener(this::onNewChat)
    }

    private fun onNewChat(e: ActionEvent?) {
        loadFromHistory()
    }
}