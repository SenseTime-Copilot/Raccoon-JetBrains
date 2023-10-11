package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.reflect.KProperty0

abstract class ChatContentBase : JPanel(), Disposable, ListDataListener, ChatLastConversationPanel.EventListener {
    interface EventListener {
        fun onSubmit(
            e: ActionEvent?,
            chatType: SenseCodeChatHistoryState.ChatType,
            conversations: List<SenseCodeChatHistoryState.Conversation>,
            onFinally: () -> Unit
        )

        fun onStopGenerate(e: ActionEvent?)
        fun onSaveHistory(history: SenseCodeChatHistoryState.History)
        fun onGotoHelpContent(e: ActionEvent?)
    }

    private val conversationListBox = Box.createVerticalBox()
    private val conversationListModel: ChatConversationListModel = ChatConversationListModel(listOf())
    private var chatLastConversationPanel: ChatLastConversationPanel? = null

    private val gotoHelpContentButton: JButton =
        ActionLink(SenseCodeBundle.message("toolwindows.content.chat.assistant.gotoHelp")).apply {
            isFocusPainted = false
            autoHideOnDisable = false
            font = JBFont.label().biggerOn(1f)
            addActionListener(this@ChatContentBase::onGotoHelpContent)
        }

    private val stopGenerateButton: JButton = JButton(SenseCodeIcons.TOOLWINDOW_STOP).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
        addActionListener(this@ChatContentBase::onStopGenerate)
        isVisible = false
    }
    private val submitButton: JButton = JButton(SenseCodeIcons.TOOLWINDOW_SUBMIT).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
        addActionListener(this@ChatContentBase::onSubmitButtonClick)
    }
    private val userPromptTextArea: JTextArea = JBTextArea().apply {
        lineWrap = true
    }
    private var lastConversationsProp: KProperty0<MutableList<String>>? = null
    private var eventListener: EventListener? = null

    private var currentChatType: SenseCodeChatHistoryState.ChatType = SenseCodeChatHistoryState.ChatType.FREE_CHAT
    override fun onNewChat(e: ActionEvent?) {
        makeUserMessage(userPromptTextArea.text)?.let {
            conversationListModel.add(SenseCodeChatHistoryState.Conversation(it))
        }
        userPromptTextArea.text = ""
        conversationListModel.takeUnless { it.isEmpty }?.let {
            eventListener?.onSaveHistory(SenseCodeChatHistoryState.History(currentChatType, it.items))
        }
        currentChatType = SenseCodeChatHistoryState.ChatType.FREE_CHAT
        conversationListModel.removeAll()
    }

    fun loadFromHistory(history: SenseCodeChatHistoryState.History) {
        onNewChat(null)
        currentChatType = history.chatType
        conversationListModel.replaceAll(history.conversations)
    }

    private fun startGenerate(e: ActionEvent? = null, isRegenerate: Boolean = false) {
        eventListener?.let { listener ->
            conversationListModel.takeUnless { it.isEmpty }?.let {
                chatLastConversationPanel?.startGenerate()
                submitButton.isVisible = false
                stopGenerateButton.isVisible = true
                if (isRegenerate) {
                    val dstIndex = it.items.lastIndex
                    it.setElementAt(SenseCodeChatHistoryState.Conversation(it.items[dstIndex].user), dstIndex)
                }
                listener.onSubmit(e, currentChatType, it.items, this::endGenerate)
            }
        }
    }

    private fun onSubmitButtonClick(e: ActionEvent?) {
        CodeClientManager.getClientAndConfigPair().second.run { models[freeChatModelName] }?.let { modelConfig ->
            makeUserMessage(
                modelConfig.freeChatPromptTemplate.displayText.format(
                    userPromptTextArea.text ?: ""
                ) + (CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let { project ->
                    SenseCodeNotification.checkEditorSelectedText(
                        modelConfig.maxInputTokens,
                        FileEditorManager.getInstance(project).selectedTextEditor
                    )?.let { "\n$it\n" }
                } ?: "")
            )?.let {
                conversationListModel.add(SenseCodeChatHistoryState.Conversation(it))
                userPromptTextArea.text = ""
                startGenerate(e)
            }
        }
    }

    fun newTask(displayText: String?, prompt: String? = null) {
        makeUserMessage(displayText, prompt)?.let {
            onNewChat(null)
            currentChatType = SenseCodeChatHistoryState.ChatType.CODE_TASK
            conversationListModel.add(SenseCodeChatHistoryState.Conversation(it))
            startGenerate(null)
        }
    }

    private fun endGenerate() {
        submitButton.isVisible = true
        stopGenerateButton.isVisible = false
        chatLastConversationPanel?.endGenerate()
    }

    private fun onGotoHelpContent(e: ActionEvent?) {
        eventListener?.onGotoHelpContent(e)
    }

    override fun onStopGenerate(e: ActionEvent?) {
        eventListener?.onStopGenerate(e)
    }

    override fun onRegenerate(e: ActionEvent?) {
        startGenerate(e, true)
    }

    fun appendAssistantText(text: String) {
        ApplicationManager.getApplication().invokeLater {
            conversationListModel.items.lastOrNull()?.let {
                it.assistant.displayText += text
                conversationListModel.contentsChanged(it)
            }
        }
    }

    fun setGenerateState(generateState: SenseCodeChatHistoryState.GenerateState) {
        ApplicationManager.getApplication().invokeLater {
            conversationListModel.items.lastOrNull()?.let {
                it.generateState = generateState
                conversationListModel.contentsChanged(it)
            }
        }
    }

    private fun addConversation(index: Int) {
        if (index > conversationListBox.components.lastIndex) {
            chatLastConversationPanel = ChatLastConversationPanel().build(
                this@ChatContentBase,
                conversationListModel.items[index],
                this@ChatContentBase
            )
            conversationListBox.add(chatLastConversationPanel, index)
            refreshConversation(index - 1)
        } else {
            conversationListBox.add(
                ChatConversationPanel().build(
                    this@ChatContentBase,
                    conversationListModel.items[index],
                    object : ChatConversationPanel.EventListener {
                        override fun onDelete(e: ActionEvent?) {
                            conversationListModel.remove(index)
                        }
                    }), index
            )
        }
    }

    private fun removeConversation(index: Int) {
        if (index >= conversationListBox.components.lastIndex) {
            chatLastConversationPanel = null
            refreshConversation(index - 1)
        }
        conversationListBox.remove(index)
    }

    private fun refreshConversation(index: Int) {
        if (index in 0..conversationListBox.components.lastIndex) {
            removeConversation(index)
            addConversation(index)
        }
    }

    override fun intervalAdded(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                addConversation(i)
                lastConversationsProp?.get()?.add(i, conversationListModel.items[i].encodeToJsonString())
            }
        }
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                removeConversation(index0)
                lastConversationsProp?.get()?.removeAt(index0)
            }
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                if (i >= conversationListBox.components.lastIndex) {
                    chatLastConversationPanel?.setAssistantText(
                        conversationListModel.items[i].assistant.displayText,
                        conversationListModel.items[i].generateState
                    )
                } else {
                    refreshConversation(i)
                }
                lastConversationsProp?.get()?.set(i, conversationListModel.items[i].encodeToJsonString())
            }
        }
    }

    init {
        layout = BorderLayout()
    }

    fun build(
        lastConversationsProp: KProperty0<MutableList<String>>,
        eventListener: EventListener
    ): ChatContentBase {
        add(UserLoginPanel(this).userLoginPanel.apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }, BorderLayout.NORTH)
        add(
            JBScrollPane(
                Box.createVerticalBox().apply {
                    add(JSeparator())
                    add(
                        Utils.createMessagePanel(
                            Utils.createRoleBox(
                                false,
                                Utils.getCurrentDatetimeString(),
                                SenseCodePlugin.NAME
                            ),
                            Utils.createContentTextPane(
                                false,
                                SenseCodeChatHistoryState.GenerateState.DONE,
                                SenseCodeBundle.message(
                                    "toolwindows.content.chat.assistant.hello",
                                    CodeClientManager.getUserName()?.let { " __@${it}__" } ?: "",
                                    "__${SenseCodePlugin.NAME}__"
                                )
                            )
                        )
                    )
                    add(Box.createHorizontalBox().apply {
                        add(Box.createHorizontalGlue())
                        add(gotoHelpContentButton)
                        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    })
                    add(JSeparator())
                    add(conversationListBox)
                },
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER
        )
        add(JPanel(BorderLayout()).apply {
            add(userPromptTextArea, BorderLayout.CENTER)
            add(Box.createHorizontalBox().apply {
                add(submitButton)
                add(stopGenerateButton)
            }, BorderLayout.EAST)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }, BorderLayout.SOUTH)

        this.eventListener = eventListener
        conversationListModel.addListDataListener(this, this)
        conversationListModel.replaceAll(lastConversationsProp.get().toConversations())
        this.lastConversationsProp = lastConversationsProp
        return this
    }

    override fun dispose() {
        eventListener = null
        lastConversationsProp = null

        gotoHelpContentButton.removeActionListener(this@ChatContentBase::onGotoHelpContent)
        stopGenerateButton.removeActionListener(this::onStopGenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)
    }

    companion object {
        @JvmStatic
        private fun makeUserMessage(displayText: String?, prompt: String? = null): SenseCodeChatHistoryState.Message? {
            if (displayText.isNullOrBlank()) {
                return null
            }
            val userName = CodeClientManager.getUserName()
            if (null == userName) {
                SenseCodeNotification.notifyLoginWithSettingsAction()
                return null
            }
            return SenseCodeChatHistoryState.Message(userName, displayText, prompt)
        }
    }
}