package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*

class ChatConversationPanel : ChatConversationPanelBase(), Disposable {
    interface EventListener {
        fun onDelete(e: ActionEvent?)
    }

    private val deleteButton: JButton = JButton(AllIcons.Welcome.Project.RemoveDisabled).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
    }
    private var eventListener: EventListener? = null
        set(value) {
            if (field !== value) {
                field?.let { deleteButton.removeActionListener(it::onDelete) }
                value?.let { deleteButton.addActionListener(it::onDelete) }
                field = value
            }
        }

    override fun dispose() {
        eventListener = null
    }

    fun build(
        parent: Disposable,
        conversation: SenseCodeChatHistoryState.Conversation,
        eventListener: EventListener
    ): ChatConversationPanel {
        super.build(conversation, deleteButton)

        this.eventListener = eventListener
        Disposer.register(parent, this)

        return this
    }
}