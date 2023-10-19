package com.sensetime.sensecore.sensecodeplugin.toolwindows.chat

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.*
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

abstract class ContentPanelBase : JPanel(BorderLayout()), ListDataListener, Disposable {
    interface EventListener {
        fun onSubmit(
            e: ActionEvent?,
            conversations: List<ChatConversation>,
            onFinally: () -> Unit
        )

        fun onStopGenerate(e: ActionEvent?)
        fun onSaveHistory(history: ChatHistory)
        fun onGotoHelpContent(e: ActionEvent?)
    }

    abstract val type: ChatHistory.ChatType

    private val gotoHelpContentButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.assistant.gotoHelp"))
    protected open val newChatButton: JButton? = null
    private val regenerateButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.regenerate"))
            .apply { addActionListener(this@ContentPanelBase::onRegenerate) }
    private val stopRegenerateButton: JButton =
        ButtonUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.stopGenerate"))
            .apply { isVisible = false }

    private val submitButton: JButton = ButtonUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_SUBMIT)
        .apply { addActionListener(this@ContentPanelBase::onSubmitButtonClick) }
    private val stopSubmitButton: JButton =
        ButtonUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_STOP).apply { isVisible = false }

    private val userPromptTextArea: JTextArea = JBTextArea().apply {
        lineWrap = true
        addFocusListener(this@ContentPanelBase, object : FocusListener {
            override fun focusGained(e: FocusEvent?) {}
            override fun focusLost(e: FocusEvent?) {
                updateLastHistoryState()
            }
        })
    }

    protected val conversationListPanel: ConversationListPanel = ConversationListPanel()

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

    protected abstract var lastHistoryState: ChatHistory
    private fun updateLastHistoryState() {
        lastHistoryState = ChatHistory(type, userPromptTextArea.text, conversationListPanel.conversationListModel.items)
    }

    override fun dispose() {
        regenerateButton.removeActionListener(this::onRegenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)

        eventListener = null
        updateLastHistoryState()
    }

    fun build(
        eventListener: EventListener
    ): ContentPanelBase {
        add(UserLoginPanel(this).userLoginPanel.apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }, BorderLayout.NORTH)
        add(
            JBScrollPane(
                Box.createVerticalBox().apply {
                    add(JSeparator())
                    add(
                        ConversationPanel.createRoleBox(
                            false,
                            SenseCodePlugin.NAME,
                            ChatConversation.getCurrentTimestampMs()
                        )
                    )
                    add(ConversationPanel.createContentTextPane(false,
                        ChatConversation.State.DONE,
                        SenseCodeBundle.message(
                            "toolwindows.content.chat.assistant.hello",
                            CodeClientManager.getUserName()?.let { " __@${it}__" } ?: "",
                            "__${SenseCodePlugin.NAME}__"
                        )))
                    add(Box.createHorizontalBox().apply {
                        add(Box.createHorizontalGlue())
                        add(gotoHelpContentButton)
                        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    })
                    add(JSeparator())
                    add(conversationListPanel)
                    add(Box.createVerticalGlue())
                },
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ), BorderLayout.CENTER
        )
        add(JPanel(BorderLayout()).apply {
            add(Box.createHorizontalBox().apply {
                newChatButton?.let { add(it) }
                add(Box.createHorizontalGlue())
                add(regenerateButton)
                add(stopRegenerateButton)
            }, BorderLayout.NORTH)
            add(userPromptTextArea, BorderLayout.CENTER)
            add(Box.createHorizontalBox().apply {
                add(submitButton)
                add(stopSubmitButton)
            }, BorderLayout.EAST)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }, BorderLayout.SOUTH)

        this.eventListener = eventListener
        userPromptTextArea.text = lastHistoryState.userPromptText
        conversationListPanel.build(this, lastHistoryState.conversations)
        updateRegenerateButtonVisible()
        conversationListPanel.conversationListModel.addListDataListener(this, this)
        return this
    }

    fun loadFromHistory(userPromptText: String = "", conversations: List<ChatConversation>? = null) {
        ChatHistory(
            type,
            userPromptTextArea.text,
            conversationListPanel.conversationListModel.items
        ).takeIf { it.hasData() }
            ?.let {
                eventListener?.onSaveHistory(it)
            }
        userPromptTextArea.text = userPromptText
        if (conversations.isNullOrEmpty()) {
            conversationListPanel.conversationListModel.removeAll()
        } else {
            conversationListPanel.conversationListModel.replaceAll(conversations)
        }
    }

    private fun onRegenerate(e: ActionEvent?) {
        startGenerate(e, true)
    }

    protected fun startGenerate(e: ActionEvent? = null, isRegenerate: Boolean = false) {
        eventListener?.let { listener ->
            conversationListPanel.conversationListModel.takeUnless { it.isEmpty }?.run {
                newChatButton?.isVisible = false
                regenerateButton.isVisible = false
                stopRegenerateButton.isVisible = true
                submitButton.isVisible = false
                stopSubmitButton.isVisible = true
                userPromptTextArea.isEditable = false

                if (isRegenerate) {
                    setElementAt(items.last().toPromptConversation(), items.lastIndex)
                }
                listener.onSubmit(e, items, this@ContentPanelBase::endGenerate)
            }
        }
    }

    private fun endGenerate() {
        if (stopRegenerateButton.isVisible) {
            updateLastHistoryState()
        }
        newChatButton?.isVisible = true
        stopRegenerateButton.isVisible = false
        submitButton.isVisible = true
        stopSubmitButton.isVisible = false
        userPromptTextArea.isEditable = true
        updateRegenerateButtonVisible()

        conversationListPanel.lastConversation?.takeIf { ChatConversation.State.PROMPT == it.state }?.let {
            if (it.assistant?.raw.isNullOrBlank()) {
                appendAssistantTextAndSetGenerateState(
                    SenseCodeBundle.message("toolwindows.content.chat.assistant.empty.stopped"),
                    ChatConversation.State.STOPPED
                )
            } else {
                setGenerateState(ChatConversation.State.STOPPED)
            }
        }
    }

    private fun onSubmitButtonClick(e: ActionEvent?) {
        makePromptConversation(
            ClientConfig.FREE_CHAT,
            ChatConversation.Message.makeMessage(userPromptTextArea.text, getSelectedText())
        )?.let {
            conversationListPanel.conversationListModel.add(it)
            userPromptTextArea.text = ""
            startGenerate(e)
        }
    }

    private fun getSelectedText(): String? =
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let { project ->
            SenseCodeNotification.checkEditorSelectedText(
                CodeClientManager.getClientAndConfigPair().second.getModelConfigByType(ClientConfig.FREE_CHAT).maxInputTokens,
                FileEditorManager.getInstance(project).selectedTextEditor
            )?.let { "\n$it\n" }
        }

    fun appendAssistantText(text: String) {
        conversationListPanel.lastConversation?.assistant?.run {
            raw += text
            conversationListPanel.lastConversationPanel?.assistantTextPane?.updateMarkDownText(raw)
        }
    }

    fun setGenerateState(generateState: ChatConversation.State) {
        conversationListPanel.lastConversation?.run {
            state = generateState
            conversationListPanel.lastConversationPanel?.assistantTextPane?.updateStyle(
                ConversationPanel.updateAssistantAttributeSet(
                    generateState
                )
            )
        }
    }

    fun appendAssistantTextAndSetGenerateState(text: String, generateState: ChatConversation.State) {
        conversationListPanel.lastConversation?.run {
            state = generateState
            val attr = ConversationPanel.updateAssistantAttributeSet(generateState)
            if (null == assistant) {
                conversationListPanel.lastConversationPanel?.assistantTextPane?.updateStyle(attr)
            } else {
                assistant.raw += text
                conversationListPanel.lastConversationPanel?.assistantTextPane?.updateMarkDownTextAndStyle(
                    assistant.raw,
                    attr
                )
            }
        }
    }

    private fun updateRegenerateButtonVisible() {
        regenerateButton.isVisible = !(conversationListPanel.conversationListModel.isEmpty)
    }

    override fun intervalAdded(e: ListDataEvent?) {
        updateLastHistoryState()
        updateRegenerateButtonVisible()
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        updateLastHistoryState()
        updateRegenerateButtonVisible()
    }

    override fun contentsChanged(e: ListDataEvent?) {
        updateLastHistoryState()
    }

    companion object {
        @JvmStatic
        fun makePromptConversation(
            type: String,
            userMessage: ChatConversation.Message?
        ): ChatConversation? {
            val userName = CodeClientManager.getUserName()
            if (null == userName) {
                SenseCodeNotification.notifyLoginWithSettingsAction()
                return null
            }
            return userMessage?.takeIf { it.hasData() }?.let { ChatConversation(userName, type, it) }
        }
    }
}