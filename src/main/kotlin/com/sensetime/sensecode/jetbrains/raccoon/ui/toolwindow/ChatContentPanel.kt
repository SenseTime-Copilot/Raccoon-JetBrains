package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.*
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ChatContentPanel(project: Project?, eventListener: EventListener? = null) : JPanel(BorderLayout()),
    ListDataListener, Disposable {
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
        get() = RaccoonChatHistoriesState.instance.lastChatHistoryString.toChatHistory()
        set(value) {
            RaccoonChatHistoriesState.instance.lastChatHistoryString = value.toJsonString()
        }

    private val gotoHelpContentButton: JButton =
        RaccoonUIUtils.createActionLinkBiggerOn1(RaccoonBundle.message("toolwindow.content.chat.assistant.gotoHelp"))

    private val newChatButton: JButton =
        RaccoonUIUtils.createActionLinkBiggerOn1(RaccoonBundle.message("toolwindow.content.chat.button.newChat"))
            .apply { addActionListener(this@ChatContentPanel::onNewChat) }
    private val regenerateButton: JButton =
        RaccoonUIUtils.createActionLinkBiggerOn1(RaccoonBundle.message("toolwindow.content.chat.button.regenerate"))
            .apply { addActionListener(this@ChatContentPanel::onRegenerate) }
    private val stopRegenerateButton: JButton =
        RaccoonUIUtils.createActionLinkBiggerOn1(RaccoonBundle.message("toolwindow.content.chat.button.stopGenerate"))
            .apply { isVisible = false }

    private val submitButton: JButton = RaccoonUIUtils.createIconButton(RaccoonIcons.TOOLWINDOW_SUBMIT)
        .apply { addActionListener(this@ChatContentPanel::onSubmitButtonClick) }
    private val stopSubmitButton: JButton =
        RaccoonUIUtils.createIconButton(RaccoonIcons.TOOLWINDOW_STOP).apply { isVisible = false }

    private val userPromptTextArea: JTextArea = JBTextArea().apply {
        lineWrap = true
        addFocusListenerWithDisposable(this@ChatContentPanel, object : FocusListener {
            override fun focusGained(e: FocusEvent?) {}
            override fun focusLost(e: FocusEvent?) {
                updateLastHistoryState()
            }
        })
        addKeyListenerWithDisposable(this@ChatContentPanel, object : KeyListener {
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
        ConversationListPanel(this, project, it.conversations)
    }

    private var scrollBar: JScrollBar? = null

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
                        content = RaccoonBundle.message(
                            "toolwindow.content.chat.assistant.hello",
                            RaccoonClientManager.userName.ifNullOrBlankElse("") { " __@${it}__" },
                            "__${name}__"
                        )
                    }.let {
//                        val assistantBackgroundColor = ColorUtil.darker(this@ChatContentPanel.getBackground(), 2)
                        add(ConversationPanel.createRoleBox(false, it.name, it.timestampMs).apply {
//                            background = assistantBackgroundColor
                        })
                        add(
                            MessagePanel(
                                project,
                                it.displayText,
                                ConversationPanel.updateAssistantAttributeSet(it.generateState)
                            ).apply {
//                                background = assistantBackgroundColor
                            })
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
            ).apply {
                scrollBar = verticalScrollBar
            }, BorderLayout.CENTER
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

        gotoEnd()
        this.eventListener = eventListener
        updateRegenerateButtonVisible()
        conversationListPanel.conversationListModel.addListDataListenerWithDisposable(this, this)
    }

    override fun dispose() {
        newChatButton.removeActionListener(this::onNewChat)
        regenerateButton.removeActionListener(this::onRegenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)
        scrollBar = null

        eventListener = null
        updateLastHistoryState()
    }

    private fun gotoEnd() {
        // todo fix trick
        RaccoonUIUtils.invokeOnUIThreadLater {
            scrollBar?.apply {
                this@ChatContentPanel.validate()
                value = maximum
            }
        }
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
        gotoEnd()
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
                RaccoonNotification.checkEditorSelectedText(
                    RaccoonSettingsState.selectedClientConfig.toolwindowModelConfig.maxInputTokens,
                    editor,
                    false
                )?.letIfNotBlank { code ->
                    ModelConfig.DisplayTextTemplate.replaceArgs(
                        ModelConfig.DisplayTextTemplate.markdownCodeTemplate,
                        mapOf(
                            ModelConfig.DisplayTextTemplate.LANGUAGE to RaccoonUtils.getMarkdownLanguage(
                                FileDocumentManager.getInstance().getFile(editor.document)
                                    ?.let { PsiManager.getInstance(project).findFile(it) }),
                            ModelConfig.DisplayTextTemplate.CODE to code
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
        conversationListPanel.conversationListModel.add(ChatConversation(userMessage))
        startGenerate()
    }

    fun setGenerateState(generateState: AssistantMessage.GenerateState) {
        conversationListPanel.lastConversation?.assistant?.run {
            this.generateState = generateState
            ConversationPanel.updateAssistantAttributeSet(generateState)?.let {
                conversationListPanel.lastConversationPanel?.assistantMessagePane?.updateStyle(it)
            }
        }
    }

    fun appendAssistantText(text: String) {
        conversationListPanel.lastConversation?.assistant?.run {
            content += text
            conversationListPanel.lastConversationPanel?.assistantMessagePane?.appendMarkdownText(text)
        }
    }

    fun appendAssistantTextAndSetGenerateState(text: String, generateState: AssistantMessage.GenerateState) {
        conversationListPanel.lastConversation?.assistant?.run {
            this.generateState = generateState
            content += text
            ConversationPanel.updateAssistantAttributeSet(generateState)?.let {
                conversationListPanel.lastConversationPanel?.assistantMessagePane?.updateStyle(it)
            }
            conversationListPanel.lastConversationPanel?.assistantMessagePane?.appendMarkdownText(text)
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
                        RaccoonBundle.message("toolwindow.content.chat.assistant.empty.stopped"),
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