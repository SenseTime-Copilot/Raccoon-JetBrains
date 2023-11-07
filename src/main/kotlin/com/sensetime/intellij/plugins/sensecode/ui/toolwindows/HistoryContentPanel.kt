package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.components.JBScrollPane
import com.sensetime.intellij.plugins.sensecode.persistent.histories.*
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal fun ChatHistory.toDisplayConversation(): ChatConversation? =
    takeIf { it.hasData() }?.let { it.conversations.firstOrNull()?.toHistoryConversation() }

internal fun List<ChatHistory>.toDisplayConversations(): List<ChatConversation> =
    mapNotNull { it.toDisplayConversation() }

class HistoryContentPanel(
    var eventListener: EventListener? = null
) : JPanel(BorderLayout()), ListDataListener, Disposable, ConversationListPanel.EventListener {
    interface EventListener {
        fun onHistoryClick(
            e: MouseEvent?,
            history: ChatHistory
        )
    }

    private var histories: List<ChatHistory>
        get() = SenseCodeChatHistoriesState.instance.historiesJsonString.toChatHistories()
        set(value) {
            SenseCodeChatHistoriesState.instance.historiesJsonString = value.toJsonString()
        }

    private val conversationListPanel: ConversationListPanel =
        ConversationListPanel(this, histories.toDisplayConversations(), this)

    private val clearButton: JButton =
        SenseCodeUIUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.history.button.clear"))
            .apply { addActionListener(this@HistoryContentPanel::onClear) }

    init {
        add(
            JBScrollPane(
                conversationListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER
        )
        add(Box.createHorizontalBox().apply {
            add(clearButton)
            add(Box.createHorizontalGlue())
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }, BorderLayout.SOUTH)

        conversationListPanel.conversationListModel.addListDataListener(this, this)
    }

    override fun dispose() {
        clearButton.removeActionListener(this::onClear)
        eventListener = null
    }

    override fun onMouseDoubleClicked(e: MouseEvent?, conversation: ChatConversation) {
        val index = conversationListPanel.conversationListModel.getElementIndex(conversation)
        histories.getOrNull(index)?.let {
            conversationListPanel.conversationListModel.remove(index)
            eventListener?.onHistoryClick(e, it)
        }
    }

    fun saveHistory(history: ChatHistory) {
        history.takeIf { it.hasData() }?.let {
            histories += it
            conversationListPanel.conversationListModel.add(it.toDisplayConversation())
        }
    }

    private fun onClear(e: ActionEvent?) {
        conversationListPanel.conversationListModel.removeAll()
    }

    override fun intervalAdded(e: ListDataEvent?) {}

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            val tmpHistories = histories
            histories = tmpHistories.subList(0, index0) + tmpHistories.subList(index1 + 1, tmpHistories.size)
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {}
}