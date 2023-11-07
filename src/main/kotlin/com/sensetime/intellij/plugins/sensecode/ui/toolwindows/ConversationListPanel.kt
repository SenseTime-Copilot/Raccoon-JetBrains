package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.sensetime.intellij.plugins.sensecode.persistent.histories.ChatConversation
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ConversationListPanel(
    parent: Disposable,
    conversations: List<ChatConversation>,
    private var eventListener: EventListener? = null
) : JPanel(BorderLayout()), ListDataListener, Disposable {
    interface EventListener {
        fun onMouseDoubleClicked(e: MouseEvent?, conversation: ChatConversation)
    }

    class ConversationListModel(items: List<ChatConversation>) : CollectionListModel<ChatConversation>(items)

    val conversationListModel: ConversationListModel = ConversationListModel(conversations)
    private val conversationListBox = Box.createVerticalBox().apply {
        for (index in conversationListModel.items.indices) {
            addConversation(index)
        }
    }

    val lastConversation: ChatConversation?
        get() = conversationListModel.items.lastOrNull()
    val lastConversationPanel: ConversationPanel?
        get() = conversationListBox.components.lastOrNull() as? ConversationPanel

    init {
        add(conversationListBox, BorderLayout.CENTER)
        conversationListModel.addListDataListener(this, this)
        Disposer.register(parent, this)
    }

    override fun dispose() {
        eventListener = null
    }

    private fun Box.addConversation(index: Int): Component = conversationListModel.items[index].let { conversation ->
        add(ConversationPanel(this@ConversationListPanel, conversation, object : ConversationPanel.EventListener {
            override fun onDelete(e: ActionEvent?) {
                conversationListModel.remove(conversation)
            }

            override fun onMouseDoubleClicked(e: MouseEvent?) {
                eventListener?.onMouseDoubleClicked(e, conversation)
            }
        }), index)
    }

    override fun intervalAdded(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                conversationListBox.addConversation(i)
            }
        }
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                conversationListBox.remove(index0)
            }
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                conversationListBox.remove(i)
                conversationListBox.addConversation(i)
            }
        }
    }
}