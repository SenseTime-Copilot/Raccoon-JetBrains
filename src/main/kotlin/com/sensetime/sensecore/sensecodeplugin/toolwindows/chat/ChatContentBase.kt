package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.toolwindows.ChatContentBase
import com.sensetime.sensecore.sensecodeplugin.toolwindows.ChatConversationListModel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.ChatConversationPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.ChatLastConversationPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ConversationListPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ConversationPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

abstract class ChatContentBase : JPanel(), Disposable, ListDataListener {
    interface EventListener {
        fun onSubmit(
            e: ActionEvent?,
            chatType: ChatHistory.Type,
            conversations: List<ChatConversation>,
            onFinally: () -> Unit
        )

        fun onStopGenerate(e: ActionEvent?)
        fun onSaveHistory(history: ChatHistory)
        fun onGotoHelpContent(e: ActionEvent?)
    }

    protected abstract val chatType: ChatHistory.Type
    abstract protected val promptTemplate: ModelConfig.PromptTemplate

    private val gotoHelpContentButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.assistant.gotoHelp"))
    private val newChatButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.newChat"))
            .apply { addActionListener(this@ChatContentBase::onNewChat) }
    private val regenerateButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.regenerate"))
            .apply { addActionListener(this@ChatContentBase::onRegenerate) }
    private val stopRegenerateButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.stopGenerate"))
            .apply { isVisible = false }

    private val submitButton: JButton = ButtonUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_SUBMIT)
        .apply { addActionListener(this@ChatContentBase::onSubmitButtonClick) }
    private val stopSubmitButton: JButton =
        ButtonUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_STOP).apply { isVisible = false }

    private val userPromptTextArea: JTextArea = JBTextArea().apply {
        lineWrap = true
    }

    private val conversationListPanel: ConversationListPanel = ConversationListPanel()
    private var conversationsStateProp: KMutableProperty0<List<ChatConversation>>? = null

    private var eventListener: EventListener? = null
        set(value) {
            if (field !== value) {
                field?.let {
                    gotoHelpContentButton.removeActionListener(it::onGotoHelpContent)
                    stopRegenerateButton.removeActionListener(it::onStopGenerate)
                    stopSubmitButton.removeActionListener(it::onStopGenerate)
                }
                value?.let {
                    gotoHelpContentButton.addActionListener(it::onGotoHelpContent)
                    stopRegenerateButton.addActionListener(it::onStopGenerate)
                    stopSubmitButton.addActionListener(it::onStopGenerate)
                }
                gotoHelpContentButton.isEnabled = (null != value)
                stopRegenerateButton.isEnabled = (null != value)
                stopSubmitButton.isEnabled = (null != value)
                field = value
            }
        }

    override fun dispose() {
        // todo add userPromptTextArea to state

        newChatButton.removeActionListener(this::onNewChat)
        regenerateButton.removeActionListener(this::onRegenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)

        eventListener = null
        conversationsStateProp = null
    }

    private fun onNewChat(e: ActionEvent?) {
        loadFromHistory()
    }

    fun loadFromHistory(conversations: List<ChatConversation>? = null) {
        (conversationListPanel.conversationListModel.items + listOfNotNull(makePromptConversation(userPromptTextArea.text))).takeIf { it.isNotEmpty() }
            ?.let {
                eventListener?.onSaveHistory(ChatHistory(chatType, it))
            }
        userPromptTextArea.text = ""
        if (conversations.isNullOrEmpty()) {
            conversationListPanel.conversationListModel.removeAll()
        } else {
            conversationListPanel.conversationListModel.replaceAll(conversations)
        }
    }

    private fun startGenerate(e: ActionEvent? = null, isRegenerate: Boolean = false) {
        eventListener?.let { listener ->
            conversationListPanel.conversationListModel.takeUnless { it.isEmpty }?.run {
                newChatButton.isVisible = false
                regenerateButton.isVisible = false
                stopRegenerateButton.isVisible = true
                submitButton.isVisible = false
                stopSubmitButton.isVisible = true
                userPromptTextArea.isEditable = false

                if (isRegenerate) {
                    setElementAt(items.last().toPromptConversation(), items.lastIndex)
                }
                listener.onSubmit(e, chatType, items, this@ChatContentBase::endGenerate)
            }
        }
    }

    private fun endGenerate() {
        newChatButton.isVisible = true
        regenerateButton.isVisible = true
        stopRegenerateButton.isVisible = false
        submitButton.isVisible = true
        stopSubmitButton.isVisible = false
        userPromptTextArea.isEditable = true
    }

    fun onRegenerate(e: ActionEvent?) {
        startGenerate(e, true)
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




        private fun makeUserMessage(userPromptText: String?, code:String?=null): ChatConversation.Message? = promptTemplate.prompt.format(userPromptText?:"") + (getSelectedText()?.let { "\n$it\n" } ?:"")
            userPromptText?.takeIf { it.isNotBlank() }?.let {
            ChatConversation.Message()
        }

        private fun makePromptConversation(userPromptText: String?, code:String?=null): ChatConversation? {
            val userName = CodeClientManager.getUserName()
            if (null == userName) {
                SenseCodeNotification.notifyLoginWithSettingsAction()
                return null
            }
            return makeUserMessage(userPromptText, code)?.let { userMessage -> ChatConversation(userName, userMessage) }
        }

    companion object {
        @JvmStatic
        private fun getSelectedText():String?
    }








    fun newTask(displayText: String?, prompt: String? = null) {
        makeUserMessage(displayText, prompt)?.let {
            onNewChat(null)
            currentChatType = SenseCodeChatHistoryState.ChatType.CODE_TASK
            conversationListModel.add(SenseCodeChatHistoryState.Conversation(it))
            startGenerate(null)
        }
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
        la

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


}