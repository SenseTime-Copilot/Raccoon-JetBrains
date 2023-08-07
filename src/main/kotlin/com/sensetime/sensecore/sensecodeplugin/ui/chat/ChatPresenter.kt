package com.sensetime.sensecore.sensecodeplugin.ui.chat

import com.intellij.openapi.project.Project
import com.sensetime.sensecore.sensecodeplugin.common.BasicTokenizer
import com.sensetime.sensecore.sensecodeplugin.common.IdGenerator
import com.sensetime.sensecore.sensecodeplugin.common.Tokenizer
import com.sensetime.sensecore.sensecodeplugin.common.UUIDIdGenerator
import com.sensetime.sensecore.sensecodeplugin.common.extensions.addNewLinesIfNeeded
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.domain.BasicPrompt
import com.sensetime.sensecore.sensecodeplugin.domain.PromptFactory
import com.sensetime.sensecore.sensecodeplugin.messagebus.CHAT_GPT_ACTION_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messagebus.ChatGptApiListener
import com.sensetime.sensecore.sensecodeplugin.openapi.OpenApi
import com.sensetime.sensecore.sensecodeplugin.openapi.RealOpenApi
import com.sensetime.sensecore.sensecodeplugin.openapi.StreamingResponse
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import com.sensetime.sensecore.sensecodeplugin.security.GptMentorCredentialsManager
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryItem
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryRepository
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.PluginStateHistoryRepository
import com.sensetime.sensecore.sensecodeplugin.ui.main.MainPresenter
import com.sensetime.sensecore.sensecodeplugin.ui.main.Tab
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ChatPresenter(
    private val chatView: ChatView,
    private val mainPresenter: MainPresenter,
    private val openApi: OpenApi = RealOpenApi(
        client = HttpClient(),
        okHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build(),
        credentialsManager = GptMentorCredentialsManager,
    ),
    private val historyRepository: HistoryRepository = PluginStateHistoryRepository(),
    private val idGenerator: IdGenerator = UUIDIdGenerator(),
    private val tokenizer: Tokenizer = BasicTokenizer(),
) : ChatGptApiListener {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiJob: Job? = null

    private val explanationBuilder = StringBuilder()
    private var chatContext: ChatContext = ChatContext(
        chatId = idGenerator.generateId(),
        chat = PromptFactory(GptMentorSettingsState.getInstance()).chat(emptyList()),
    )
    private var charsTyped = StringBuilder()
    private lateinit var project: Project

    fun onAttach(project: Project) {
        project.messageBus.connect().subscribe(CHAT_GPT_ACTION_TOPIC, this)
    }

    /**
     * Handles text deleted from the prompt. Used to update the token count.
     */
    fun promptTextDeleted(length: Int) {
        if (charsTyped.length >= length) {
            charsTyped = charsTyped.delete(charsTyped.length - length, charsTyped.length)
        } else {
            charsTyped = StringBuilder()
        }
        countTokensAndDisplay(charsTyped.toString())
    }

    fun promptPastedFromClipboard(text: String) {
        charsTyped.append(text.addNewLinesIfNeeded(2))
        countTokensAndDisplay(charsTyped.toString())
    }

    private fun countTokensAndDisplay(text: String) {
        val tokens = tokenizer.countTokens(text)
        chatView.updateNumberOfTokens("Approx. $tokens tokens")
    }

    private fun resetTokens() {
        chatView.updateNumberOfTokens("Approx. 0 tokens")
        charsTyped.clear()
    }

    fun onSubmitClicked() {
        val prompt = chatView.getPrompt()

        if (prompt.isEmpty()) {
            return
        }

        chatView.setPrompt(prompt)
        chatContext = chatContext.addMessage(prompt, ChatGptRequest.Message.Role.USER)
        executeStreaming(chatContext.chat)
    }

    private fun executeStreaming(prompt: BasicPrompt) {
        chatView.showLoading()
        charsTyped.clear()
        apiJob?.cancel()
        apiJob = scope.launch {
            chatView.appendToExplanation(prompt.action.addNewLinesIfNeeded(1))
            kotlin.runCatching {
                val state = GptMentorSettingsState.getInstance()
                val chatGptRequest = prompt.createRequest(
                    model = state.model,
                    temperature = state.temperature,
                    maxTokens = state.maxTokens,
                )
                openApi.executeBasicActionStreaming(chatGptRequest)
                    .collect { streamingResponse ->
                        handleResponse(streamingResponse)
                    }
            }.onFailure {
                it.printStackTrace()
                if (it !is CancellationException) {
                    chatView.showError(it.message ?: "Unknown error")
                }
            }
        }
    }

    private fun handleResponse(streamingResponse: StreamingResponse) {
        when (streamingResponse) {
            is StreamingResponse.Data -> handleData(streamingResponse.data)
            is StreamingResponse.Error -> handleError(streamingResponse.error)
            StreamingResponse.Done -> handleDone()
        }
    }

    private fun handleData(data: String) {
        explanationBuilder.append(data)
        chatView.appendExplanation(data)
    }

    private fun handleError(error: String) {
        chatView.showError(error)
        chatView.hideLoading()
        chatView.updateNumberOfTokens("")
    }

    private fun handleDone() {
        chatView.hideLoading()
        chatView.onExplanationDone()
        chatView.clearPrompt()
        chatView.updateNumberOfTokens("")
        chatContext = chatContext.addMessage(explanationBuilder.toString(), ChatGptRequest.Message.Role.SYSTEM)
        historyRepository.addOrUpdateHistoryItem(HistoryItem.from(chatContext, state = GptMentorSettingsState.getInstance()))
    }

    fun onStopClicked() {
        chatView.hideLoading()
        apiJob?.cancel()
    }

    override fun onNewPrompt(prompt: BasicPrompt) {
        explanationBuilder.clear()
        chatContext = ChatContext.fromPrompt(id = idGenerator.generateId(), prompt = prompt)

        countTokensAndDisplay(prompt.action)
        chatView.setPrompt(prompt.action, positionCursorAtEnd = prompt.executeImmediate.not())
        chatView.clearExplanation()
        if (prompt.executeImmediate) {
            executeStreaming(prompt)
        }

        mainPresenter.selectTab(Tab.CHAT)
    }

    override fun appendToPrompt(prompt: String) {
        chatView.appendToPrompt(prompt.addNewLinesIfNeeded(2))
        mainPresenter.selectTab(Tab.CHAT)
    }

    override fun loadChatFromHistory(historyItem: HistoryItem) {
        chatContext = historyItem.getChatContext().also { context ->
            resetAll()
            when (context.chat) {
                is BasicPrompt.Chat -> {
                    context.chat.messages.forEach { message ->
                        when (message.role) {
                            ChatGptRequest.Message.Role.USER -> {
                                chatView.appendToExplanation(message.content.addNewLinesIfNeeded(2))
                            }

                            ChatGptRequest.Message.Role.SYSTEM -> {
                                chatView.appendExplanation(message.content.addNewLinesIfNeeded(2))
                            }
                        }
                    }
                }

                else -> {
                    chatView.appendToExplanation(chatContext.chat.action)
                }
            }
        }

        mainPresenter.selectTab(Tab.CHAT)
    }

    fun onNewChatClicked() {
        apiJob?.cancel()
        chatContext = ChatContext(
            chatId = idGenerator.generateId(),
            chat = PromptFactory(GptMentorSettingsState.getInstance()).chat(emptyList()),
        )

        resetAll()
    }

    private fun resetAll() {
        explanationBuilder.clear()
        chatView.clearAll()
        chatView.hideLoading()
        chatView.setFocusOnPrompt()
        resetTokens()
    }
}
