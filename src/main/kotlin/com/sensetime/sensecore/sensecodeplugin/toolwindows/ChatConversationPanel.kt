package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*

class ChatConversationPanel : ChatConversationPanelBase(), Disposable {



    fun build(
        parent: Disposable,
        conversation: SenseCodeChatHistoryState.Conversation,
        eventListener: EventListener
    ): ChatConversationPanel {
        super.build(conversation, deleteButton)

        this.eventListener = eventListener


        return this
    }
}