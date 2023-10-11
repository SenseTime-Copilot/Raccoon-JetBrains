package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.CodeStreamResponse
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_TASKS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messages.SenseCodeTasksListener
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.awt.event.ActionEvent

class SenseCodeToolWindowFactory : ToolWindowFactory, DumbAware, Disposable {
    var toolWindowJob: Job? = null

    private var taskMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val helpContent = toolWindow.contentManager.factory.createContent(
            HelpContentPanel(),
            SenseCodeBundle.message("toolwindows.content.help.title"),
            false
        )

        var chatContentPanel: ChatContentBase = ChatContentPanel()
        val historyContentPanel = HistoryContentPanel {
            chatContentPanel.loadFromHistory(it)
            toolWindow.contentManager.run {
                setSelectedContent(getContent(chatContentPanel))
            }
        }
        chatContentPanel = chatContentPanel.build(
            SenseCodeChatHistoryState.instance::lastFreeChatConversations, object : ChatContentBase.EventListener {
                override fun onSubmit(
                    e: ActionEvent?,
                    chatType: SenseCodeChatHistoryState.ChatType,
                    conversations: List<SenseCodeChatHistoryState.Conversation>,
                    onFinally: () -> Unit
                ) {
                    toolWindowJob?.cancel()
                    val settings = SenseCodeSettingsState.instance
                    val (client, config) = CodeClientManager.getClientAndConfigPair()
                    val modelName = when (chatType) {
                        SenseCodeChatHistoryState.ChatType.FREE_CHAT -> config.freeChatModelName
                        SenseCodeChatHistoryState.ChatType.CODE_TASK -> config.actionsModelName
                    }
                    val model = config.models.getValue(modelName)
                    val responseFlow = client.requestStream(
                        CodeRequest(
                            model.name,
                            Utils.toCodeRequestMessage(conversations),
                            model.temperature,
                            1,
                            model.stop,
                            model.getMaxNewTokens(settings.completionPreference),
                            config.apiEndpoint
                        )
                    )

                    toolWindowJob = CodeClientManager.clientCoroutineScope.launch {
                        responseFlow.onCompletion { onFinally() }.collect { streamResponse ->
                            when (streamResponse) {
                                CodeStreamResponse.Done -> chatContentPanel.setGenerateState(SenseCodeChatHistoryState.GenerateState.DONE)

                                is CodeStreamResponse.Error -> {
                                    chatContentPanel.appendAssistantText(streamResponse.error)
                                    chatContentPanel.setGenerateState(SenseCodeChatHistoryState.GenerateState.ERROR)
                                }

                                is CodeStreamResponse.TokenChoices -> streamResponse.choices.firstOrNull()?.token?.takeIf { it.isNotEmpty() }
                                    ?.let {
                                        chatContentPanel.appendAssistantText(it)
                                    }

                                else -> {}
                            }
                        }
                    }
                }

                override fun onStopGenerate(e: ActionEvent?) {
                    toolWindowJob?.cancel()
                    toolWindowJob = null
                }

                override fun onSaveHistory(history: SenseCodeChatHistoryState.History) {
                    historyContentPanel.saveHistory(history)
                }

                override fun onGotoHelpContent(e: ActionEvent?) {
                    toolWindow.contentManager.run {
                        setSelectedContent(helpContent)
                    }
                }
            })

        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                chatContentPanel, SenseCodeBundle.message("toolwindows.content.chat.title"), false
            ).apply { setDisposer(this) }
        )
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                historyContentPanel,
                SenseCodeBundle.message("toolwindows.content.history.title"),
                false
            )
        )
        toolWindow.contentManager.addContent(helpContent)

        taskMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(SENSE_CODE_TASKS_TOPIC, object : SenseCodeTasksListener {
                override fun onNewTask(displayText: String, prompt: String?) {
                    ApplicationManager.getApplication().invokeLater {
                        toolWindow.contentManager.run {
                            setSelectedContent(getContent(chatContentPanel))
                        }
                        toolWindow.show()
                        chatContentPanel.newTask(displayText, prompt)
                    }
                }
            })
        }
    }

    override fun dispose() {
        toolWindowJob?.cancel()
        toolWindowJob = null
        taskMessageBusConnection = null
    }
}