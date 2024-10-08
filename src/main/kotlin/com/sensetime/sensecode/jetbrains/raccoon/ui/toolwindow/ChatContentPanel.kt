package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCodeChunk
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.PromptVariables
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.replaceVariables
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_SENSITIVE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.*
import com.sensetime.sensecode.jetbrains.raccoon.utils.*
import kotlinx.coroutines.Job
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.*
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener


internal class ChatContentPanel(private val project: Project, eventListener: EventListener? = null) :
    JPanel(BorderLayout()),
    RaccoonSensitiveListener, ListDataListener, Disposable {
    interface EventListener {
        fun onSubmit(
            e: ActionEvent?,
            action: String,
            conversations: List<ChatConversation>,
            localKnowledge: List<LLMCodeChunk>?,
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

    private val userPromptTextArea: JTextArea = object : JBTextArea() {
        override fun paintBorder(g: Graphics?) {
            (g?.create() as? Graphics2D)?.let { g2 ->
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.ActionButton.focusedBorder()
                if (isFocusOwner) {
                    g2.stroke = BasicStroke(1.5F)
                }
                g2.drawRoundRect(0, 0, width - 1, height - 1, 16, 16)
            }
        }
    }.apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(RaccoonUIUtils.MEDIUM_GAP_SIZE, RaccoonUIUtils.SMALL_GAP_SIZE)
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

    private val loadingLabel: JLabel = JLabel(AnimatedIcon.Big.INSTANCE).apply { isVisible = false }
    private val buttonJPanel: JPanel

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
        add(LLMClientManager.currentLLMClient.makeUserAuthorizationPanel(this).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }, BorderLayout.NORTH)
        add(
            JBScrollPane(
                Box.createVerticalBox().apply {
                    add(JSeparator())
                    AssistantMessage(generateState = AssistantMessage.GenerateState.DONE).apply {
                        content = RaccoonBundle.message(
                            "toolwindow.content.chat.assistant.hello",
                            RaccoonUserInformation.getInstance().getDisplayUserName()
                                .ifNullOrBlankElse("") { " __@${it}__" },
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
                    add(loadingLabel)
                    add(conversationListPanel)
                    add(Box.createVerticalGlue())
                },
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ).apply {
                val mouseListener = object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent?) {
                        updateAutoScrolling()
                    }

                    override fun mouseDragged(e: MouseEvent?) {
                        updateAutoScrolling()
                    }

                    override fun mouseWheelMoved(e: MouseWheelEvent?) {
                        updateAutoScrolling()
                    }
                }
                scrollBar = verticalScrollBar.apply {
                    addMouseListener(mouseListener)
                    addMouseMotionListener(mouseListener)
                }
                addMouseWheelListener(mouseListener)
            }, BorderLayout.CENTER
        )
        buttonJPanel = JPanel(BorderLayout()).apply {
            add(Box.createHorizontalBox().apply {
                add(newChatButton)
                add(Box.createHorizontalGlue())
                add(regenerateButton)
                add(stopRegenerateButton)
                border = JBUI.Borders.emptyBottom(RaccoonUIUtils.SMALL_GAP_SIZE)
            }, BorderLayout.NORTH)
            add(userPromptTextArea, BorderLayout.CENTER)
            add(Box.createHorizontalBox().apply {
                add(submitButton)
                add(stopSubmitButton)
                border = JBUI.Borders.emptyLeft(RaccoonUIUtils.SMALL_GAP_SIZE)
            }, BorderLayout.EAST)
            border = JBUI.Borders.empty(10)
        }
        add(buttonJPanel, BorderLayout.SOUTH)

        gotoEnd()
        this.eventListener = eventListener
        updateRegenerateButtonVisible()
        conversationListPanel.conversationListModel.addListDataListenerWithDisposable(this, this)

        addAncestorListener(object : AncestorListenerAdapter() {
            override fun ancestorAdded(event: AncestorEvent?) {
                sensitiveJob = getStartTime()?.let { startTime ->
                    startSensitiveFilter()
                    LLMClientManager.getInstance(project).launchClientJob {
                        var sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation> =
                            emptyMap()
                        RaccoonExceptions.resultOf({
                            sensitiveConversations = it.getSensitiveConversations(startTime, action = "chat visible")
                        }) {
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

    private fun updateAutoScrolling() {
        scrollBar?.let {
            conversationListPanel.lastConversationPanel?.assistantMessagePane?.isAutoScrolling =
                (it.value + it.visibleAmount + 20 >= it.maximum)
        }
    }

    private fun getStartTime(): String? {
        var minTime: Long? = null
        conversationListPanel.conversationListModel.items.forEach { conversation ->
            if (!conversation.id.isNullOrBlank() && ((null == minTime) || (conversation.user.timestampMs < minTime!!))) {
                minTime = conversation.user.timestampMs
            }
        }
        return minTime?.toString()
    }

    override fun dispose() {
        sensitiveJob = null
        sensitiveBusConnection = null
        newChatButton.removeActionListener(this::onNewChat)
        regenerateButton.removeActionListener(this::onRegenerate)
        submitButton.removeActionListener(this::onSubmitButtonClick)
        scrollBar = null

        eventListener = null
        updateLastHistoryState()
    }

    private fun gotoEnd() {
        // todo fix trick
        RaccoonUIUtils.invokeOnEdtLater {
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

    fun onNewChat(e: ActionEvent?) {
        loadFromHistory()
    }

    private var lastLocalKnowledge: List<LLMCodeChunk>? = null
    private fun startGenerate(
        action: String,
        e: ActionEvent? = null,
        isRegenerate: Boolean = false,
        localKnowledge: List<LLMCodeChunk>? = null
    ) {
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
                } else {
                    lastLocalKnowledge = localKnowledge
                }
                gotoEnd()
                listener.onSubmit(e, action, items, lastLocalKnowledge, this@ChatContentPanel::endGenerate)
                if (!isRegenerate) {
                    ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                        .onToolWindowQuestionSubmitted()
                    if (items.size == 1) {
                        ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                            .onToolWindowNewSession()
                    }
                } else {
                    ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                        .onToolWindowRegenerateClicked()
                }
            }
        }
    }

    private fun onRegenerate(e: ActionEvent?) {
        startGenerate("regenerate", e, true)
    }

    private fun getSelectedCode(): Pair<String, List<LLMCodeChunk>?> =
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let { project ->
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                RaccoonNotification.checkEditorSelectedText(
                    RaccoonClient.clientConfig.chatModelConfig.maxInputTokens,
                    editor, project,
                    false
                )?.let { (code, localKnowledge) ->
                    PromptVariables.markdownCodeTemplate.replaceVariables(
                        mapOf(
                            PromptVariables.LANGUAGE to RaccoonLanguages.getMarkdownLanguageFromPsiFile(
                                FileDocumentManager.getInstance().getFile(editor.document)
                                    ?.let { PsiManager.getInstance(project).findFile(it) }),
                            PromptVariables.CODE to code
                        )
                    ).let { Pair(it, localKnowledge) }
                }
            }
        }.let { result ->
            Pair(result?.first.ifNullOrBlankElse("") { "\n$it\n" }, result?.second)
        }

    private fun onSubmitButtonClick(e: ActionEvent?) {
        userPromptTextArea.text?.letIfNotBlank { userInputText ->
            val (code, localKnowledge) = getSelectedCode()
            UserMessage.createUserMessage(
                project,
                promptType = ChatModelConfig.FREE_CHAT,
                text = userInputText + code
            )
                ?.let {
                    conversationListPanel.conversationListModel.add(
                        ChatConversation(
                            it,
                            id = RaccoonUtils.generateUUID()
                        )
                    )
                    userPromptTextArea.text = ""
                    startGenerate("free chat", e, localKnowledge = localKnowledge)
                }
        }
    }

    fun newTask(userMessage: UserMessage, localKnowledge: List<LLMCodeChunk>?) {
        conversationListPanel.conversationListModel.add(ChatConversation(userMessage, id = RaccoonUtils.generateUUID()))
        startGenerate(userMessage.promptType, localKnowledge = localKnowledge)
    }

    fun setGenerateState(generateState: AssistantMessage.GenerateState) {
        conversationListPanel.lastConversation?.assistant?.run {
            val (tmpText, newGenerateState) = updateGenerateState(generateState)
            tmpText.letIfNotBlank {
                conversationListPanel.lastConversationPanel?.assistantMessagePane?.appendMarkdownText(
                    it
                )
            }
            ConversationPanel.updateAssistantAttributeSet(newGenerateState)?.let {
                conversationListPanel.lastConversationPanel?.assistantMessagePane?.updateStyle(it)
            }
            conversationListPanel.lastConversationPanel?.assistantMessagePane?.checkGenerateStateForStatistics(
                newGenerateState
            )
            if (newGenerateState == AssistantMessage.GenerateState.DONE) {
                ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                    .onToolWindowAnswerFinished()
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
            content += text
            val (tmpText, newGenerateState) = updateGenerateState(generateState)
            conversationListPanel.lastConversationPanel?.assistantMessagePane?.appendMarkdownText(
                tmpText.takeIfNotBlank() ?: text
            )
            ConversationPanel.updateAssistantAttributeSet(newGenerateState)?.let {
                conversationListPanel.lastConversationPanel?.assistantMessagePane?.updateStyle(it)
            }
        }
    }

    fun setLastConversationToSensitive(errorMessage: String) {
        conversationListPanel.conversationListModel.items.lastIndex.takeIf { it >= 0 }?.let { index ->
            conversationListPanel.conversationListModel.setElementAt(
                conversationListPanel.conversationListModel.items[index].toSensitiveConversation(
                    errorMessage
                ), index
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
//                if (it.hasData()) {
                setGenerateState(AssistantMessage.GenerateState.STOPPED)
//                } else {
//                    appendAssistantTextAndSetGenerateState(
//                        RaccoonBundle.message("toolwindow.content.chat.assistant.empty.stopped"),
//                        AssistantMessage.GenerateState.STOPPED
//                    )
//                }
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

    fun startSensitiveFilter() {
        loadingLabel.isVisible = true
        buttonJPanel.isVisible = false
        conversationListPanel.isVisible = false
    }

    fun stopSensitiveFilter(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        invokeOnEdtLater {
            runSensitiveFilter(sensitiveConversations)
            loadingLabel.isVisible = false
            buttonJPanel.isVisible = true
            conversationListPanel.isVisible = true
            gotoEnd()
        }
    }

    private fun runSensitiveFilter(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        if (sensitiveConversations.isEmpty()) {
            return
        }
        val indexForRemove: MutableList<Int> = mutableListOf()
        conversationListPanel.conversationListModel.items.forEachIndexed { index, conversation ->
            if (null != conversation.id?.takeIf { it.isNotBlank() && sensitiveConversations.containsKey(it) }) {
                indexForRemove.add(index)
            }
        }
        indexForRemove.reversed().forEach {
            conversationListPanel.conversationListModel.remove(it)
        }
    }

    override fun onNewSensitiveConversations(sensitiveConversations: Map<String, RaccoonSensitiveListener.SensitiveConversation>) {
        invokeOnEdtLater {
            runSensitiveFilter(sensitiveConversations)
        }
    }
}