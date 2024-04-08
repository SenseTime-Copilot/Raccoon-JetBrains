package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.Urls
import com.sensetime.sensecode.jetbrains.raccoon.clients.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChatChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.*
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.*
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.Job
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import kotlin.coroutines.cancellation.CancellationException


internal class RaccoonToolWindowFactory : ToolWindowFactory, DumbAware, Disposable {
    private var chatJob: Job? = null
        set(value) {
            field.takeIf { true == it?.isActive }?.cancel()
            field = value
        }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val historyContentPanel = HistoryContentPanel(project)
        val chatContentPanel = ChatContentPanel(project)
        chatContentPanel.eventListener = object : ChatContentPanel.EventListener {
            override fun onSubmit(
                e: ActionEvent?,
                action: String,
                conversations: List<ChatConversation>,
                onFinally: () -> Unit
            ) {
                chatJob = LLMClientManager.getInstance(project).launchLLMChatJob(
                    true,
                    toolWindow.component,
                    LLMChatRequest(
                        conversations.getID() ?: RaccoonUtils.generateUUID(),
                        action = action,
                        messages = conversations.toCodeRequestMessage(RaccoonClient.clientConfig.chatModelConfig)
                    ),
                    object : LLMClientManager.LLMJobListener<LLMChatChoice, String?>,
                        LLMClient.LLMUsagesResponseListener<LLMChatChoice>() {
                        override fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<LLMChatChoice>) {
                            llmResponse.throwIfError()
                            llmResponse.choices?.firstOrNull()?.token?.takeIf { it.isNotEmpty() }
                                ?.let {
                                    chatContentPanel.appendAssistantText(it)
                                }
                            super.onResponseInsideEdtAndCatching(llmResponse)
                        }

                        override fun onDoneInsideEdtAndCatching(): String? {
                            chatContentPanel.setGenerateState(AssistantMessage.GenerateState.DONE)
                            return super.onDoneInsideEdtAndCatching()
                        }

                        override fun onFailureWithoutCancellationInsideEdt(t: Throwable) {
                            if (t is LLMClientSensitiveException) {
                                chatContentPanel.setLastConversationToSensitive(t.localizedMessage)
                            } else if (t !is CancellationException) {
                                chatContentPanel.appendAssistantTextAndSetGenerateState(
                                    t.localizedMessage,
                                    AssistantMessage.GenerateState.ERROR
                                )
                            }
                        }

                        override fun onFinallyInsideEdt() {
                            onFinally()
                        }
                    })
            }

            override fun onStopGenerate(e: ActionEvent?) {
                chatJob = null
            }

            override fun onSaveHistory(history: ChatHistory) {
                historyContentPanel.saveHistory(history)
            }

            override fun onGotoHelpContent(e: ActionEvent?) {
                BrowserUtil.browse(
                    Urls.newFromEncoded("https://raccoon.sensetime.com/code/docs")
                        .addParameters(buildMap<String, String> {
                            put("ide", "jetBrains")
                            put("utm_source", "JetBrains ${RaccoonPlugin.ideName}")
                            RaccoonBundle.message("login.dialog.link.web.lang").letIfNotBlank { put("lang", it) }
                        }).toExternalForm()
                )
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

        project.messageBus.connect().also {
            it.subscribe(RACCOON_TASKS_TOPIC, object : RaccoonTasksListener {
                override fun onNewTask(userMessage: UserMessage) {
                    toolWindow.contentManager.run {
                        setSelectedContent(getContent(chatContentPanel))
                    }
                    toolWindow.show()
                    if (null == chatJob) {
                        chatContentPanel.newTask(userMessage)
                    } else {
                        LLMClientManager.getInstance(project).launchClientJob {
                            chatJob?.run {
                                cancel()
                                join()
                            }
                        }.invokeOnCompletion {
                            RaccoonUIUtils.invokeOnEdtLater {
                                chatContentPanel.newTask(userMessage)
                            }
                        }
                    }
                }
            })
            Disposer.register(this) { it.disconnect() }
        }

        chatContentPanel.onNewChat(null)
    }

    override fun dispose() {
        chatJob = null
    }
}