package com.sensetime.sensecore.sensecodeplugin.toolwindows.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.components.JBScrollPane
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.*
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

fun ChatHistory.toDisplayConversation(): ChatConversation? =
    takeIf { it.hasData() }?.let { it.conversations.firstOrNull()?.toHistoryConversation() }

fun List<ChatHistory>.toDisplayConversations(): List<ChatConversation> = mapNotNull { it.toDisplayConversation() }

class HistoryContentPanel : JPanel(BorderLayout()), ListDataListener, Disposable {
    interface EventListener {
        fun onHistoryClick(
            e: MouseEvent?,
            history: ChatHistory
        )
    }

    private val histories: MutableList<ChatHistory> =
        SenseCodeChatHistoryState.instance.historiesJsonString.toChatHistories().toMutableList()
    private val conversationListPanel: ConversationListPanel = ConversationListPanel()
    private var eventListener: EventListener? = null

    private fun updateHistoriesState() {
        SenseCodeChatHistoryState.instance.historiesJsonString = histories.toJsonString()
    }

    override fun dispose() {
        eventListener = null
        updateHistoriesState()
    }

    fun build(
        eventListener: EventListener
    ): HistoryContentPanel {
        add(
            JBScrollPane(
                conversationListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER
        )
        this.eventListener = eventListener
        conversationListPanel.build(
            this,
            histories.toDisplayConversations(),
            object : ConversationListPanel.EventListener {
                override fun onMouseDoubleClicked(e: MouseEvent?, index: Int) {
                    histories.getOrNull(index)?.let {
                        conversationListPanel.conversationListModel.remove(index)
                        this@HistoryContentPanel.eventListener?.onHistoryClick(e, it)
                    }
                }
            })
        conversationListPanel.conversationListModel.addListDataListener(this, this)
        return this
    }

    fun saveHistory(history: ChatHistory) {
        history.takeIf { it.hasData() }?.let {
            histories.add(it)
            conversationListPanel.conversationListModel.add(it.toDisplayConversation())
        }
    }

    override fun intervalAdded(e: ListDataEvent?) {
        updateHistoriesState()
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                histories.removeAt(index0)
            }
        }
        updateHistoriesState()
    }

    override fun contentsChanged(e: ListDataEvent?) {
        updateHistoriesState()
    }
}