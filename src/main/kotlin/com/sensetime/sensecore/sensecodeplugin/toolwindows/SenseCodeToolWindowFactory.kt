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
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState
import com.sensetime.sensecore.sensecodeplugin.toolwindows.chat.ChatContentPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.chat.ContentPanelBase
import com.sensetime.sensecore.sensecodeplugin.toolwindows.chat.TaskContentPanel
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.toCodeRequestMessage
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.ChatHistory
import com.sensetime.sensecore.sensecodeplugin.toolwindows.history.HistoryContentPanel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent

class SenseCodeToolWindowFactory : ToolWindowFactory, DumbAware, Disposable {
    private var chatJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var taskJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    private var taskMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    private fun onSubmit(
        contentPanel: ContentPanelBase,
        conversations: List<ChatConversation>,
        onFinally: () -> Unit
    ): Job {
        val maxNewTokens: Int = SenseCodeSettingsState.instance.toolwindowMaxNewTokens
        val (client, clientConfig) = CodeClientManager.getClientAndConfigPair()
        val modelConfig = clientConfig.getModelConfigByType(contentPanel.type.code)
        val responseFlow = client.requestStream(
            CodeRequest(
                modelConfig.name,
                conversations.toCodeRequestMessage(),
                modelConfig.temperature,
                1,
                modelConfig.stop,
                if (maxNewTokens <= 0) modelConfig.getMaxNewTokens(ModelConfig.CompletionPreference.BEST_EFFORT) else maxNewTokens,
                clientConfig.apiEndpoint
            )
        )

        return CodeClientManager.clientCoroutineScope.launch {
            responseFlow.onCompletion { onFinally() }.collect { streamResponse ->
                when (streamResponse) {
                    CodeStreamResponse.Done -> {
                        contentPanel.setGenerateState(ChatConversation.State.DONE)
                        onFinally()
                    }

                    is CodeStreamResponse.Error -> {
                        contentPanel.appendAssistantTextAndSetGenerateState(
                            streamResponse.error,
                            ChatConversation.State.ERROR
                        )
                        onFinally()
                    }

                    is CodeStreamResponse.TokenChoices -> streamResponse.choices.firstOrNull()?.token?.takeIf { it.isNotEmpty() }
                        ?.let {
                            contentPanel.appendAssistantText(it)
                        }

                    else -> {}
                }
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val helpContent = toolWindow.contentManager.factory.createContent(
            HelpContentPanel(),
            SenseCodeBundle.message("toolwindows.content.help.title"),
            false
        )

        val chatContentPanel = ChatContentPanel()
        val taskContentPanel = TaskContentPanel()
        val historyContentPanel = HistoryContentPanel()
        chatContentPanel.build(object : ContentPanelBase.EventListener {
            override fun onSubmit(e: ActionEvent?, conversations: List<ChatConversation>, onFinally: () -> Unit) {
                chatJob = onSubmit(chatContentPanel, conversations, onFinally)
            }

            override fun onStopGenerate(e: ActionEvent?) {
                chatJob = null
            }

            override fun onSaveHistory(history: ChatHistory) {
                historyContentPanel.saveHistory(history)
            }

            override fun onGotoHelpContent(e: ActionEvent?) {
                toolWindow.contentManager.run {
                    setSelectedContent(helpContent)
                }
            }
        })
        taskContentPanel.build(object : ContentPanelBase.EventListener {
            override fun onSubmit(e: ActionEvent?, conversations: List<ChatConversation>, onFinally: () -> Unit) {
                taskJob = onSubmit(taskContentPanel, conversations, onFinally)
            }

            override fun onStopGenerate(e: ActionEvent?) {
                taskJob = null
            }

            override fun onSaveHistory(history: ChatHistory) {
                historyContentPanel.saveHistory(history)
            }

            override fun onGotoHelpContent(e: ActionEvent?) {
                toolWindow.contentManager.run {
                    setSelectedContent(helpContent)
                }
            }

        })
        historyContentPanel.build(object : HistoryContentPanel.EventListener {
            override fun onHistoryClick(e: MouseEvent?, history: ChatHistory) {
                when (history.chatType) {
                    ChatHistory.ChatType.FREE_CHAT -> chatContentPanel
                    ChatHistory.ChatType.CODE_TASK -> taskContentPanel
                }.let {
                    it.loadFromHistory(history.userPromptText, history.conversations)
                    toolWindow.contentManager.run {
                        setSelectedContent(getContent(it))
                    }
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
                taskContentPanel, SenseCodeBundle.message("toolwindows.content.task.title"), false
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
                override fun onNewTask(type: String, userMessage: ChatConversation.Message) {
                    ApplicationManager.getApplication().invokeLater {
                        toolWindow.contentManager.run {
                            setSelectedContent(getContent(taskContentPanel))
                        }
                        toolWindow.show()
                        taskContentPanel.newTask(type, userMessage)
                    }
                }
            })
        }
    }

    override fun dispose() {
        chatJob = null
        taskJob = null
        taskMessageBusConnection = null
    }
}