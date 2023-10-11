package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.Box
import javax.swing.JButton

class ChatLastConversationPanel : ChatConversationPanelBase(), Disposable {
    interface EventListener {
        fun onNewChat(e: ActionEvent?)
        fun onStopGenerate(e: ActionEvent?)
        fun onRegenerate(e: ActionEvent?)
    }

    private val newChatButton: JButton =
        ActionLink(SenseCodeBundle.message("toolwindows.content.chat.button.newChat")).apply {
            isFocusPainted = false
            autoHideOnDisable = false
        }
    private val stopGenerateButton: JButton =
        ActionLink(SenseCodeBundle.message("toolwindows.content.chat.button.stopGenerate")).apply {
            isFocusPainted = false
            autoHideOnDisable = false
        }
    private val regenerateButton: JButton =
        ActionLink(SenseCodeBundle.message("toolwindows.content.chat.button.regenerate")).apply {
            isFocusPainted = false
            autoHideOnDisable = false
        }
    private var eventListener: EventListener? = null
        set(value) {
            if (field !== value) {
                field?.let {
                    newChatButton.removeActionListener(it::onNewChat)
                    stopGenerateButton.removeActionListener(it::onStopGenerate)
                    regenerateButton.removeActionListener(it::onRegenerate)
                }
                value?.let {
                    newChatButton.addActionListener(it::onNewChat)
                    stopGenerateButton.addActionListener(it::onStopGenerate)
                    regenerateButton.addActionListener(it::onRegenerate)
                }
                field = value
            }
        }

    override fun dispose() {
        endGenerate()
        eventListener = null
    }

    fun build(
        parent: Disposable,
        conversation: SenseCodeChatHistoryState.Conversation,
        eventListener: EventListener
    ): ChatLastConversationPanel {
        super.build(conversation, null)

        add(Box.createHorizontalBox().apply {
            add(Box.createHorizontalStrut(10))
            add(newChatButton)
            add(Box.createHorizontalGlue())
            add(stopGenerateButton)
            add(regenerateButton)
            add(Box.createHorizontalStrut(10))
        }, BorderLayout.SOUTH)
        endGenerate()
        this.eventListener = eventListener
        Disposer.register(parent, this)

        return this
    }

    fun startGenerate() {
        newChatButton.isVisible = false
        regenerateButton.isVisible = false
        stopGenerateButton.isVisible = true
    }

    fun endGenerate() {
        newChatButton.isVisible = true
        regenerateButton.isVisible = true
        stopGenerateButton.isVisible = false
    }
}