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
        fun onMouseDoubleClicked(e: MouseEvent?, conversation: ChatConversation)
    }

    class ConversationListModel(items: List<ChatConversation>) : CollectionListModel<ChatConversation>(items)

    private val conversationListBox = Box.createVerticalBox()
    val conversationListModel: ConversationListModel = ConversationListModel(emptyList())
    val lastConversation: ChatConversation?
        get() = conversationListModel.items.lastOrNull()
    val lastConversationPanel: ConversationPanel?
        get() = conversationListBox.components.lastOrNull() as? ConversationPanel
    var eventListener: EventListener? = null

    fun build(
        parent: Disposable,
        conversations: List<ChatConversation>,
        eventListener: EventListener? = null
    ): ConversationListPanel {
        conversationListModel.addListDataListener(this, this)
        conversationListModel.replaceAll(conversations)

        add(conversationListBox, BorderLayout.CENTER)
        this.eventListener = eventListener

        Disposer.register(parent, this)
        return this
    }

    override fun dispose() {
        eventListener = null
    }

    private fun addConversation(index: Int) {
        conversationListBox.add(
            conversationListModel.items[index].let { conversation ->
                ConversationPanel().build(
                    this, conversation,
                    object : ConversationPanel.EventListener {
                        override fun onDelete(e: ActionEvent?) {
                            conversationListModel.remove(conversation)
                        }

                        override fun onMouseDoubleClicked(e: MouseEvent?) {
                            eventListener?.onMouseDoubleClicked(e, conversation)
                        }
                    })
            }, index
        )
    }

    private fun removeConversation(index: Int) {
        conversationListBox.remove(index)
    }

    override fun intervalAdded(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                addConversation(i)
            }
        }
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                removeConversation(index0)
            }
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                removeConversation(i)
                addConversation(i)
            }
        }
    }
}