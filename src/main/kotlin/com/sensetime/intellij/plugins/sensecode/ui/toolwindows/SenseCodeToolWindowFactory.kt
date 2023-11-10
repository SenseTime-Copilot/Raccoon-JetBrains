package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.clients.requests.CodeRequest
import com.sensetime.intellij.plugins.sensecode.clients.responses.CodeStreamResponse
import com.sensetime.intellij.plugins.sensecode.persistent.histories.*
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.topics.SENSE_CODE_TASKS_TOPIC
import com.sensetime.intellij.plugins.sensecode.topics.SenseCodeTasksListener
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import kotlin.coroutines.cancellation.CancellationException

class SenseCodeToolWindowFactory : ToolWindowFactory, DumbAware, Disposable {
    private var chatJob: Job? = null
        set(value) {
            field.takeIf { true == it?.isActive }?.cancel()
            field = value
        }

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

        val historyContentPanel = HistoryContentPanel()
        val chatContentPanel = ChatContentPanel()
        chatContentPanel.eventListener = object : ChatContentPanel.EventListener {
            override fun onSubmit(e: ActionEvent?, conversations: List<ChatConversation>, onFinally: () -> Unit) {
                val maxNewTokens: Int = SenseCodeSettingsState.instance.toolwindowMaxNewTokens
                val (client, clientConfig) = SenseCodeClientManager.clientAndConfigPair
                val modelConfig = clientConfig.toolwindowModelConfig

                chatJob = SenseCodeClientManager.clientCoroutineScope.launch {
                    try {
                        client.requestStream(
                            CodeRequest(
                                modelConfig.name,
                                conversations.toCodeRequestMessage(modelConfig),
                                modelConfig.temperature,
                                1,
                                modelConfig.stop,
                                if (maxNewTokens <= 0) modelConfig.getMaxNewTokens(ModelConfig.CompletionPreference.BEST_EFFORT) else maxNewTokens,
                                clientConfig.toolwindowApiPath
                            )
                        ) { streamResponse ->
                            SenseCodeUIUtils.invokeOnUIThreadLater {
                                when (streamResponse) {
                                    CodeStreamResponse.Done -> chatContentPanel.setGenerateState(AssistantMessage.GenerateState.DONE)
                                    is CodeStreamResponse.Error -> chatContentPanel.appendAssistantTextAndSetGenerateState(
                                        streamResponse.error,
                                        AssistantMessage.GenerateState.ERROR
                                    )

                                    is CodeStreamResponse.TokenChoices -> streamResponse.choices.firstOrNull()?.token?.takeIf { it.isNotEmpty() }
                                        ?.let {
                                            chatContentPanel.appendAssistantText(it)
                                        }

                                    else -> {}
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        if (t !is CancellationException) {
                            chatContentPanel.appendAssistantTextAndSetGenerateState(
                                t.localizedMessage,
                                AssistantMessage.GenerateState.ERROR
                            )
                        }
                    } finally {
                        SenseCodeUIUtils.invokeOnUIThreadLater(onFinally)
                    }
                }
            }

            override fun onStopGenerate(e: ActionEvent?) {
                chatJob = null
            }

            override fun onSaveHistory(history: ChatHistory) {
                historyContentPanel.saveHistory(history)
            }

            override fun onGotoHelpContent(e: ActionEvent?) {
                toolWindow.contentManager.setSelectedContent(helpContent)
            }
        }


        historyContentPanel.eventListener = object : HistoryContentPanel.EventListener {
            override fun onHistoryClick(e: MouseEvent?, history: ChatHistory) {
                chatContentPanel.let {
                    it.loadFromHistory(history.userPromptText, history.conversations)
                    toolWindow.contentManager.run {
                        setSelectedContent(getContent(it))
                    }
                }
            }
        }

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
                override fun onNewTask(userMessage: UserMessage) {
                    toolWindow.contentManager.run {
                        setSelectedContent(getContent(chatContentPanel))
                    }
                    toolWindow.show()
                    if (null == chatJob) {
                        chatContentPanel.newTask(userMessage)
                    } else {
                        SenseCodeClientManager.clientCoroutineScope.launch {
                            chatJob?.run {
                                cancel()
                                join()
                            }
                        }.invokeOnCompletion {
                            SenseCodeUIUtils.invokeOnUIThreadLater {
                                chatContentPanel.newTask(userMessage)
                            }
                        }
                    }
                }
            })
        }
    }

    override fun dispose() {
        chatJob = null
        taskMessageBusConnection = null
    }
}