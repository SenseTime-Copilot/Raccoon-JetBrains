package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.persistent.histories.*
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeIcons
import com.sensetime.intellij.plugins.sensecode.ui.SenseCodeNotification
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import com.sensetime.intellij.plugins.sensecode.ui.common.UserAuthorizationPanelBuilder
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeUtils
import com.sensetime.intellij.plugins.sensecode.utils.ifNullOrBlankElse
import com.sensetime.intellij.plugins.sensecode.utils.letIfNotBlank
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ChatContentPanel(eventListener: EventListener? = null) : JPanel(BorderLayout()), ListDataListener, Disposable {
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

    private var lastHistoryState: ChatHistory
        get() = SenseCodeChatHistoriesState.instance.lastChatHistoryString.toChatHistory()
        set(value) {
            SenseCodeChatHistoriesState.instance.lastChatHistoryString = value.toJsonString()
        }

    private val gotoHelpContentButton: JButton =
        SenseCodeUIUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.assistant.gotoHelp"))

    private val newChatButton: JButton =
        SenseCodeUIUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.newChat"))
            .apply { addActionListener(this@ChatContentPanel::onNewChat) }
    private val regenerateButton: JButton =
        SenseCodeUIUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.regenerate"))
            .apply { addActionListener(this@ChatContentPanel::onRegenerate) }
    private val stopRegenerateButton: JButton =
        SenseCodeUIUtils.createActionLinkBiggerOn1(SenseCodeBundle.message("toolwindows.content.chat.button.stopGenerate"))
            .apply { isVisible = false }

    private val submitButton: JButton = SenseCodeUIUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_SUBMIT)
        .apply { addActionListener(this@ChatContentPanel::onSubmitButtonClick) }
    private val stopSubmitButton: JButton =
        SenseCodeUIUtils.createIconButton(SenseCodeIcons.TOOLWINDOW_STOP).apply { isVisible = false }

    private val userPromptTextArea: JTextArea = JBTextArea().apply {
        lineWrap = true
        addFocusListener(this@ChatContentPanel, object : FocusListener {
            override fun focusGained(e: FocusEvent?) {}
            override fun focusLost(e: FocusEvent?) {
                updateLastHistoryState()
            }
        })
        addKeyListener(this@ChatContentPanel, object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {
                e?.takeIf { it.keyCode == KeyEvent.VK_ENTER }?.let { keyEvent ->
                    if (keyEvent.isControlDown) {
                        val lastText = text
                        val lastCaretPosition = caretPosition
                        text = lastText.substring(0, lastCaretPosition) + System.lineSeparator() + lastText.substring(
                            lastCaretPosition
                        )
                        caretPosition = lastCaretPosition + 1
                    } else {
                        keyEvent.consume()
                        onSubmitButtonClick(null)
                    }
                }
            }

            override fun keyReleased(e: KeyEvent?) {}
        })
    }

    private val conversationListPanel: ConversationListPanel = lastHistoryState.let {
        userPromptTextArea.text = it.userPromptText
        ConversationListPanel(this, it.conversations)
    }

    var eventListener: EventListener? = null
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

    init {
        add(UserAuthorizationPanelBuilder().build(this).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }, BorderLayout.NORTH)
        add(
            JBScrollPane(
                Box.createVerticalBox().apply {
                    add(JSeparator())
                    AssistantMessage(generateState = AssistantMessage.GenerateState.DONE).apply {
                        content = SenseCodeBundle.message(
                            "toolwindows.content.chat.assistant.hello",
                            SenseCodeClientManager.userName.ifNullOrBlankElse("") { " __@${it}__" },
                            "__${name}__"
                        )
                    }.let {
                        add(ConversationPanel.createRoleBox(false, it.name, it.timestampMs))
                        add(ConversationPanel.createContentTextPane(false, it.displayText, it.generateState))
                    }

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
                add(newChatButton)
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
        updateRegenerateButtonVisible()
        conversationListPanel.conversationListModel.addListDataListener(this, this)
    }

    override fun dispose() {
        newChatButton.removeActionListener(this::onNewChat)
        regenerateButton.removeActionListener(this::onRegenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)

        eventListener = null
        updateLastHistoryState()
    }

    fun loadFromHistory(userPromptText: String = "", conversations: List<ChatConversation>? = null) {
        ChatHistory(
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

    private fun onNewChat(e: ActionEvent?) {
        loadFromHistory()
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
                listener.onSubmit(e, items, this@ChatContentPanel::endGenerate)
            }
        }
    }

    private fun onRegenerate(e: ActionEvent?) {
        startGenerate(e, true)
    }

    private fun getSelectedCode(): String =
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let { project ->
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                SenseCodeNotification.checkEditorSelectedText(
                    SenseCodeSettingsState.selectedClientConfig.toolwindowModelConfig.maxInputTokens,
                    editor,
                    false
                )?.letIfNotBlank { code ->
                    ModelConfig.DisplayTextTemplate.replaceArgs(
                        ModelConfig.DisplayTextTemplate.markdownCodeTemplate,
                        mapOf(
                            ModelConfig.DisplayTextTemplate.LANGUAGE to SenseCodeUtils.getMarkdownLanguage(
                                PsiManager.getInstance(project).findFile(editor.virtualFile)
                            ), ModelConfig.DisplayTextTemplate.CODE to code
                        )
                    )
                }
            }
        }.ifNullOrBlankElse("") { "\n$it\n" }

    private fun onSubmitButtonClick(e: ActionEvent?) {
        userPromptTextArea.text?.letIfNotBlank { userInputText ->
            UserMessage.createUserMessage(promptType = ModelConfig.FREE_CHAT, text = userInputText + getSelectedCode())
                ?.let {
                    conversationListPanel.conversationListModel.add(ChatConversation(it))
                    userPromptTextArea.text = ""
                    startGenerate(e)
                }
        }
    }

    fun newTask(userMessage: UserMessage) {
        endGenerate()
        conversationListPanel.conversationListModel.add(ChatConversation(userMessage))
        startGenerate()
    }

    fun setGenerateState(generateState: AssistantMessage.GenerateState) {
        conversationListPanel.lastConversation?.assistant?.run {
            this.generateState = generateState
            conversationListPanel.lastConversationPanel?.assistantTextPane?.updateStyle(
                ConversationPanel.updateAssistantAttributeSet(
                    generateState
                )
            )
        }
    }

    fun appendAssistantText(text: String) {
        conversationListPanel.lastConversation?.assistant?.run {
            content += text
            conversationListPanel.lastConversationPanel?.assistantTextPane?.updateMarkDownText(displayText)
        }
    }

    fun appendAssistantTextAndSetGenerateState(text: String, generateState: AssistantMessage.GenerateState) {
        conversationListPanel.lastConversation?.assistant?.run {
            this.generateState = generateState
            content += text
            conversationListPanel.lastConversationPanel?.assistantTextPane?.updateMarkDownTextAndStyle(
                displayText,
                ConversationPanel.updateAssistantAttributeSet(generateState)
            )
        }
    }

    private fun endGenerate() {
        if (stopRegenerateButton.isVisible) {
            updateLastHistoryState()
        }
        newChatButton.isVisible = true
        stopRegenerateButton.isVisible = false
        submitButton.isVisible = true
        stopSubmitButton.isVisible = false
        userPromptTextArea.isEditable = true
        updateRegenerateButtonVisible()

        conversationListPanel.lastConversation?.assistant?.takeIf { AssistantMessage.GenerateState.PROMPT == it.generateState }
            ?.let {
                if (it.hasData()) {
                    setGenerateState(AssistantMessage.GenerateState.STOPPED)
                } else {
                    appendAssistantTextAndSetGenerateState(
                        SenseCodeBundle.message("toolwindows.content.chat.assistant.empty.stopped"),
                        AssistantMessage.GenerateState.STOPPED
                    )
                }
            }
    }

    private fun updateLastHistoryState() {
        lastHistoryState = ChatHistory(userPromptTextArea.text, conversationListPanel.conversationListModel.items)
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
}