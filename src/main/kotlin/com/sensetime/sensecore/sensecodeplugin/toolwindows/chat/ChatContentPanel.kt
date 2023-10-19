package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.SenseCodeChatHistoryState
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.toJsonString
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import java.awt.event.ActionEvent
import javax.swing.JButton

class ChatContentPanel : ContentPanelBase() {
    override val type: ChatHistory.ChatType = ChatHistory.ChatType.FREE_CHAT
    override var lastHistoryState: ChatHistory
        get() = SenseCodeChatHistoryState.instance.lastChatHistoryString.toChatHistory()
        set(value) {
            SenseCodeChatHistoryState.instance.lastChatHistoryString = value.toJsonString()
        }

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