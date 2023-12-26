package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.CodeStreamResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_TASKS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonTasksListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import kotlinx.coroutines.Job
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import kotlin.coroutines.cancellation.CancellationException

class RaccoonToolWindowFactory : ToolWindowFactory, DumbAware, Disposable {
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
        val historyContentPanel = HistoryContentPanel(project)
        val chatContentPanel = ChatContentPanel(project)
        chatContentPanel.eventListener = object : ChatContentPanel.EventListener {
            override fun onSubmit(e: ActionEvent?, conversations: List<ChatConversation>, onFinally: () -> Unit) {
                val maxNewTokens: Int = RaccoonSettingsState.instance.toolwindowMaxNewTokens
                val (client, clientConfig) = RaccoonClientManager.clientAndConfigPair
                val modelConfig = clientConfig.toolwindowModelConfig

                chatJob = RaccoonClientManager.launchClientJob {
                    try {
                        client.requestStream(
                            CodeRequest(
                                conversations.getID(),
                                modelConfig.name,
                                conversations.toCodeRequestMessage(modelConfig),
                                modelConfig.temperature,
                                1,
                                modelConfig.stop,
                                if (maxNewTokens <= 0) modelConfig.getMaxNewTokens(ModelConfig.CompletionPreference.BEST_EFFORT) else maxNewTokens,
                                clientConfig.toolwindowApiPath
                            )
                        ) { streamResponse ->
                            RaccoonUIUtils.invokeOnUIThreadLater {
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
                        RaccoonUIUtils.invokeOnUIThreadLater(onFinally)
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
                BrowserUtil.browse("https://github.com/SenseTime-Copilot/Raccoon-JetBrains/blob/main/README.md")
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

            override fun onNotLogin() {
                toolWindow.contentManager.run {
                    setSelectedContent(getContent(chatContentPanel))
                }
            }
        }

        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                chatContentPanel, RaccoonBundle.message("toolwindow.content.chat.title"), false
            ).apply { setDisposer(this) }
        )
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                historyContentPanel,
                RaccoonBundle.message("toolwindow.content.history.title"),
                false
            )
        )

        taskMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(SENSE_CODE_TASKS_TOPIC, object : RaccoonTasksListener {
                override fun onNewTask(userMessage: UserMessage) {
                    toolWindow.contentManager.run {
                        setSelectedContent(getContent(chatContentPanel))
                    }
                    toolWindow.show()
                    if (null == chatJob) {
                        chatContentPanel.newTask(userMessage)
                    } else {
                        RaccoonClientManager.launchClientJob {
                            chatJob?.run {
                                cancel()
                                join()
                            }
                        }.invokeOnCompletion {
                            RaccoonUIUtils.invokeOnUIThreadLater {
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