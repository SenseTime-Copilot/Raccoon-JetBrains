package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.CodeClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.*
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_SENSITIVE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.addListDataListenerWithDisposable
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnUIThreadLater
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal fun ChatHistory.toDisplayConversation(): ChatConversation? =
    takeIf { it.hasData() }?.let { it.conversations.firstOrNull()?.toHistoryConversation() }

internal fun List<ChatHistory>.toDisplayConversations(): List<ChatConversation> =
    mapNotNull { it.toDisplayConversation() }

class HistoryContentPanel(
    project: Project,
    var eventListener: EventListener? = null
) : JPanel(BorderLayout()), ListDataListener, RaccoonSensitiveListener, Disposable,
    ConversationListPanel.EventListener {
    interface EventListener {
        fun onHistoryClick(
            e: MouseEvent?,
            history: ChatHistory
        )

        fun onNotLogin()
    }

    private var histories: List<ChatHistory>
        get() = RaccoonChatHistoriesState.instance.historiesJsonString.toChatHistories()
        set(value) {
            RaccoonChatHistoriesState.instance.historiesJsonString = value.toJsonString()
        }

    private val conversationListPanel: ConversationListPanel =
        ConversationListPanel(this, project, histories.toDisplayConversations(), this)

    private val clearButton: JButton =
        RaccoonUIUtils.createActionLinkBiggerOn1(RaccoonBundle.message("toolwindow.content.history.button.clear"))
            .apply { addActionListener(this@HistoryContentPanel::onClear) }

    private val loadingLabel: JLabel = JLabel(AnimatedIcon.Big.INSTANCE).apply { isVisible = false }
    private val buttonBox: Box = Box.createHorizontalBox().apply {
        add(clearButton)
        add(Box.createHorizontalGlue())
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    private var sensitiveJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var sensitiveBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    init {
        add(loadingLabel, BorderLayout.NORTH)
        add(
            JBScrollPane(
                conversationListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER
        )
        add(buttonBox, BorderLayout.SOUTH)

        conversationListPanel.conversationListModel.addListDataListenerWithDisposable(this, this)
        addAncestorListener(object : AncestorListenerAdapter() {
            override fun ancestorAdded(event: AncestorEvent?) {
                sensitiveJob = getStartTime()?.let { startTime ->
                    startSensitiveFilter()
                    RaccoonClientManager.launchClientJob {
                        var sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation> =
                            emptyMap()
                        try {
                            sensitiveConversations = it.getSensitiveConversations(startTime)
                        } catch (e: Throwable) {
                            if (e is CodeClient.UnauthorizedException) {
                                eventListener?.onNotLogin()
                                invokeOnUIThreadLater { RaccoonNotification.notifyGotoLogin() }
                            }
                        } finally {
                            stopSensitiveFilter(sensitiveConversations)
                        }
                    }
                }
            }
        })

        sensitiveBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(RACCOON_SENSITIVE_TOPIC, this)
        }
    }

    private fun getStartTime(): String? {
        var minTime: Long? = null
        histories.forEach { chatHistory ->
            chatHistory.conversations.forEach { conversation ->
                if (!conversation.id.isNullOrBlank() && ((null == minTime) || (conversation.user.timestampMs < minTime!!))) {
                    minTime = conversation.user.timestampMs
                }
            }
        }
        return minTime?.toString()
    }

    override fun dispose() {
        clearButton.removeActionListener(this::onClear)
        eventListener = null
        sensitiveJob = null
        sensitiveBusConnection = null
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
        conversationListPanel.repaint()
        conversationListPanel.revalidate()
    }

    override fun intervalAdded(e: ListDataEvent?) {}

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            val tmpHistories = histories
            histories = tmpHistories.subList(0, index0) + tmpHistories.subList(index1 + 1, tmpHistories.size)
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {}

    fun startSensitiveFilter() {
        loadingLabel.isVisible = true
        buttonBox.isVisible = false
        conversationListPanel.isVisible = false
    }

    fun stopSensitiveFilter(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        invokeOnUIThreadLater {
            runSensitiveFilter(sensitiveConversations)
            loadingLabel.isVisible = false
            buttonBox.isVisible = true
            conversationListPanel.isVisible = true
        }
    }

    private fun runSensitiveFilter(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        if (sensitiveConversations.isEmpty()) {
            return
        }
        var historiesHasChange = false
        val historyIndexForRemove: MutableList<Int> = mutableListOf()
        val tmpHistories: MutableList<ChatHistory> = mutableListOf()
        histories.forEachIndexed { historyIndex, chatHistory ->
            var uiHasChange = false
            tmpHistories.add(
                ChatHistory(
                    chatHistory.userPromptText,
                    chatHistory.conversations.filterIndexed { conversationIndex, chatConversation ->
                        (null == chatConversation.id?.takeIf {
                            it.isNotBlank() && sensitiveConversations.containsKey(
                                it
                            )
                        }).also { isNotSensitive ->
                            if (!isNotSensitive) {
                                historiesHasChange = true
                                if (conversationIndex <= 0) {
                                    uiHasChange = true
                                }
                            }
                        }
                    },
                    chatHistory.timestampMs
                ).also { newChatHistory ->
                    if (!newChatHistory.hasData()) {
                        historyIndexForRemove.add(historyIndex)
                        historiesHasChange = true
                    } else if (uiHasChange) {
                        conversationListPanel.conversationListModel.setElementAt(
                            newChatHistory.toDisplayConversation()!!,
                            historyIndex
                        )
                    }
                })
        }
        if (historiesHasChange) {
            histories = tmpHistories
        }
        historyIndexForRemove.reversed().forEach { historyIndex ->
            conversationListPanel.conversationListModel.remove(historyIndex)
        }
    }

    override fun onNewSensitiveConversations(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        invokeOnUIThreadLater {
            runSensitiveFilter(sensitiveConversations)
        }
    }
}