package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

open class ConversationListPanel : JPanel(BorderLayout()), ListDataListener, Disposable {
    interface EventListener {
        fun onMouseDoubleClicked(e: MouseEvent?, index: Int)
    }

    class ConversationListModel(items: List<ChatConversation>) : CollectionListModel<ChatConversation>(items)

    private val conversationListBox = Box.createVerticalBox()
    val conversationListModel: ConversationListModel = ConversationListModel(emptyList())
    val lastConversationPanel: ConversationPanel?
        get() = conversationListBox.components.lastOrNull() as? ConversationPanel
    var eventListener: EventListener? = null

    fun build(
        parent: Disposable,
        conversations: List<ChatConversation>,
        eventListener: EventListener? = null
    ): ConversationListPanel {
        addConversations(0, conversations)
        add(conversationListBox, BorderLayout.CENTER)

        conversationListModel.replaceAll(conversations)
        conversationListModel.addListDataListener(this, this)

        this.eventListener = eventListener
        Disposer.register(parent, this)
        return this
    }

    override fun dispose() {
        eventListener = null
    }

    private fun addConversations(index: Int, conversations: List<ChatConversation>) {
        conversations.forEachIndexed { i, conversation ->
            val dstIndex = index + i
            conversationListBox.add(
                ConversationPanel().build(
                    this,
                    conversation,
                    object : ConversationPanel.EventListener {
                        override fun onDelete(e: ActionEvent?) {
                            conversationListModel.remove(dstIndex)
                        }

                        override fun onMouseDoubleClicked(e: MouseEvent?) {
                            eventListener?.onMouseDoubleClicked(e, dstIndex)
                        }
                    }), dstIndex
            )
        }
    }

    private fun removeConversations(index0: Int, index1: Int) {
        for (i in index0..index1) {
            conversationListBox.remove(i)
        }
    }

    private fun replaceConversations(index: Int, conversations: List<ChatConversation>) {
        conversations.forEachIndexed { i, conversation ->
            (conversationListBox.components[index + i] as ConversationPanel).conversation = conversation
        }
    }

    override fun intervalAdded(e: ListDataEvent?) {
        e?.run {
            addConversations(index0, conversationListModel.items.subList(index0, index1 + 1))
        }
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            removeConversations(index0, index1)
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {
        e?.run {
            replaceConversations(index0, conversationListModel.items.subList(index0, index1 + 1))
        }
    }
}