package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.applyIf
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMAgentRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnEdtSync
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.toCoroutineContext
import com.sensetime.sensecode.jetbrains.raccoon.utils.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.awt.Component
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JPanel
import kotlin.coroutines.resumeWithException


internal abstract class LLMClient {
    // common

    abstract val name: String
    protected abstract val clientConfig: ClientConfig

    abstract suspend fun onUnauthorizedInsideEdt(isEnableNotify: Boolean, project: Project?)
    abstract fun makeUserAuthorizationPanel(parent: Disposable): JPanel

    // sensitive
    open suspend fun getSensitiveConversations(
        startTime: String, endTime: String? = null, action: String
    ): Map<String, RaccoonSensitiveListener.SensitiveConversation> = emptyMap()


    // client job: contains one or more requests, corresponding to a state transition

    protected class ClientJobRunner(
        isEnableDebugLog: Boolean
    ) {
        companion object {
            private val currentRequestID: AtomicLong = AtomicLong(0L)
        }

        val id: Long = currentRequestID.getAndIncrement()
        private val startTimeMs = RaccoonUtils.getSteadyTimestampMs()
        val debugLogger: Logger? = LOG.takeIf { isEnableDebugLog }

        fun getCostS(): String =
            String.format("%.3fs", (RaccoonUtils.getSteadyTimestampMs() - startTimeMs) / 1000F)

        // ignore all topic exceptions
        inline fun Project.publishStateTopicWithCatching(block: RaccoonRequestStateListener.() -> Unit) {
            RaccoonExceptions.resultOf { messageBus.syncPublisher(RACCOON_REQUEST_STATE_TOPIC).block() }
        }

        fun Request.printIfEnableDebugLog(): Request = also { okRequest ->
            debugLogger?.debug { "Request[$id]: \"${okRequest.method} ${okRequest.url}\"" }
            debugLogger?.debug { "Body[$id]: \"${okRequest.body}\"" }
        }

        fun String.printResponseBodyIfEnableDebugLog(): String = also { body ->
            debugLogger?.debug { "Response[$id]: \"$body\"" }
        }
    }

    // internal use, must be invoked inside the EDT, return R if succeeded, otherwise throw exceptions
    private suspend fun <R> ClientJobRunner.runClientJobInsideEdt(
        isEnableNotify: Boolean,
        project: Project?,
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = try {
        clientJobInsideEdtAndCatching().also { result ->
            debugLogger?.debug { "Request[$id]: Done, cost ${getCostS()}. \"$result\"" }
        }
    } catch (t: Throwable) {
        LOG.warnWithDebug(t)
        debugLogger?.debug { "Request[$id]: Failed, cost ${getCostS()}. \"$t\"" }
        if (t is LLMClientUnauthorizedException) {
            onUnauthorizedInsideEdt(isEnableNotify, project)
        }
        throw t
    }

    // run a client job synchronously, return R if succeeded, otherwise throw exceptions
    private suspend fun <R> ClientJobRunner.runClientJob(
        isEnableNotify: Boolean,
        project: Project?,
        uiComponentForEdt: Component?,
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = withContext(Dispatchers.Main.immediate.plusIfNotNull(uiComponentForEdt?.toCoroutineContext())) {
        runClientJobInsideEdt(isEnableNotify, project, clientJobInsideEdtAndCatching)
    }

    // run a client job with publish state topic synchronously, return R if succeeded, otherwise throw exceptions
    private suspend fun <R> ClientJobRunner.runClientJobWithStateTopic(
        action: String?, isEnableNotify: Boolean,
        projectForTopic: Project, uiComponentForEdt: Component?,
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = withContext(Dispatchers.Main.immediate.plusIfNotNull(uiComponentForEdt?.toCoroutineContext())) {
        try {
            projectForTopic.publishStateTopicWithCatching { onStartInsideEdtAndCatching(id, action) }
            runClientJobInsideEdt(isEnableNotify, projectForTopic) {
                clientJobInsideEdtAndCatching().also { result ->
                    projectForTopic.publishStateTopicWithCatching {
                        onDoneInsideEdtAndCatching(id, result?.let { "$it" })
                    }
                    debugLogger?.debug { "Request[$id]: done message is \"$result\"" }
                }
            }
        } catch (t: Throwable) {
            projectForTopic.publishStateTopicWithCatching { onFailureIncludeCancellationInsideEdtAndCatching(id, t) }
            throw t
        } finally {
            projectForTopic.publishStateTopicWithCatching { onFinallyInsideEdtAndCatching(id) }
        }
    }


    // request via okhttp3
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS).build()
    private val sseEventSourceFactory: EventSource.Factory = EventSources.createFactory(okHttpClient)

    protected open suspend fun onSuccessfulHeaderInsideEdtAndCatching(headers: Headers) {}
    protected abstract fun decodeToLLMResponseError(body: String): LLMResponseError
    private fun throwLLMClientResponseException(response: Response): Nothing {
        // because the body can only be gotten once
        val responseBody =
            response.body?.string()?.also { LOG.debug { "$name: request failed. response body is \"$it\"" } }

        // 1. found error in body. do not display 5xx server error, ignore decode exceptions
        try {
            RaccoonExceptions.resultOf {
                responseBody?.takeUnless { response.isHttpServerError() }?.let { decodeToLLMResponseError(it) }
            }.getOrNull()?.throwIfError()
        } catch (llmClientMessageException: LLMClientMessageException) {
            if (401 == response.code) {
                throw LLMClientUnauthorizedException(llmClientMessageException)
            } else {
                throw LLMClientResponseException(llmClientMessageException, response)
            }
        }

        // 2. http code error
        response.takeUnless { it.isSuccessful }?.let {
            throw LLMClientResponseException(
                it,
                "Http code: ${it.code}${(it.message.takeIfNotBlank() ?: responseBody)?.ifNullOrBlankElse("") { details -> "\n$details" }}"
            )
        }

        // 3. unknown error
        throw LLMClientUnknownException()
    }

    protected open fun createRequestBuilderWithCommonHeader(
        apiEndpoint: String, stream: Boolean
    ): Request.Builder = Request.Builder().url(apiEndpoint).header("Content-Type", "application/json")
        .addHeader("Date", RaccoonUtils.getFormattedUTCDate())
        .applyIf(stream) { addHeader("Accept", "text/event-stream") }

    protected abstract suspend fun Request.Builder.addAuthorizationHeaderInsideEdtAndCatching(clientJobRunner: ClientJobRunner): Request.Builder
    protected suspend fun ClientJobRunner.createRequestBuilderWithCommonHeaderAndAuthorization(
        apiEndpoint: String, stream: Boolean
    ): Request.Builder =
        createRequestBuilderWithCommonHeader(apiEndpoint, stream).addAuthorizationHeaderInsideEdtAndCatching(this)

    // return body if succeeded, otherwise throw exceptions
    protected suspend fun ClientJobRunner.requestInsideEdtAndCatching(okRequest: Request): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = okHttpClient.newCall(okRequest.printIfEnableDebugLog())
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            response.takeIf { it.isSuccessful } ?: throwLLMClientResponseException(response)
                        })
                    }
                })
                continuation.invokeOnCancellation {
                    RaccoonExceptions.resultOf { call.cancel() }
                }
            }
        }.let { response ->
            onSuccessfulHeaderInsideEdtAndCatching(response.headers)
            requireNotNull(response.body).string().printResponseBodyIfEnableDebugLog()
        }

    // onSuccessfulBodyInsideEdtAndCatching must return not null(as result R) when Done. throw exceptions if failed
    private suspend fun <R> ClientJobRunner.streamRequestInsideCatching(
        okRequest: Request,
        uiComponentForEdt: Component?,
        onSuccessfulBodyInsideEdtAndCatching: (String) -> R?
    ): R = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val eventSource =
                sseEventSourceFactory.newEventSource(okRequest.printIfEnableDebugLog(), object : EventSourceListener() {
                    private var successfulHeader: Headers? = null
                    private var result: R? = null
                        get() = field?.takeIf { (it as? Boolean) != false }

                    override fun onOpen(eventSource: EventSource, response: Response) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        if (response.isSuccessful) {
                            successfulHeader = response.headers
                        } else {
                            throwLLMClientResponseException(response)
                        }
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        if (null != result) {
                            throw LLMClientStreamException("receive data after DONE")
                        }
                        result = uiComponentForEdt.invokeOnEdtSync {
                            onSuccessfulBodyInsideEdtAndCatching(data.printResponseBodyIfEnableDebugLog())
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            result?.let { Pair(it, successfulHeader) }
                                ?: throw LLMClientStreamException("stream not received DONE")
                        })
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            t?.let { throw it }
                            response?.let { throwLLMClientResponseException(it) }
                            throw LLMClientUnknownException()
                        })
                    }
                })
            continuation.invokeOnCancellation {
                RaccoonExceptions.resultOf { eventSource.cancel() }
            }
        }
    }.let { (result, successfulHeader) ->
        successfulHeader?.let { onSuccessfulHeaderInsideEdtAndCatching(it) }
        result
    }

    protected suspend fun <R> runClientJob(
        isEnableNotify: Boolean,
        isEnableDebugLog: Boolean,
        project: Project?,
        uiComponentForEdt: Component?,
        clientJobInsideEdtAndCatching: suspend ClientJobRunner.() -> R
    ): R = ClientJobRunner(isEnableDebugLog).run {
        runClientJob(
            isEnableNotify, project, uiComponentForEdt
        ) { clientJobInsideEdtAndCatching() }
    }

    // run one request client job via okhttp3 synchronously, return R if succeeded, otherwise throw exceptions
    protected suspend fun <R> Request.runRequestJob(
        isEnableNotify: Boolean,
        isEnableDebugLog: Boolean,
        project: Project?,
        uiComponentForEdt: Component?,
        onResponseInsideEdtAndCatching: suspend ClientJobRunner.(String) -> R
    ): R = runClientJob(isEnableNotify, isEnableDebugLog, project, uiComponentForEdt) {
        onResponseInsideEdtAndCatching(requestInsideEdtAndCatching(this@runRequestJob))
    }


    // LLM request

    protected abstract fun Request.Builder.addLLMBodyInsideEdtAndCatching(llmRequest: LLMRequest): Request.Builder

    private fun LLMRequest.getApiEndpointFromConfig(): String = when (this) {
        is LLMAgentRequest -> clientConfig.getAgentApiEndpoint()
        is LLMChatRequest -> clientConfig.getChatApiEndpoint()
        is LLMCompletionRequest -> clientConfig.getCompletionApiEndpoint()
    }

    private suspend fun ClientJobRunner.llmToOkRequestInsideEdtAndCatching(llmRequest: LLMRequest): Request =
        createRequestBuilderWithCommonHeaderAndAuthorization(
            llmRequest.getApiEndpointFromConfig(), llmRequest.isStream()
        ).addLLMBodyInsideEdtAndCatching(llmRequest).build()

    private fun isLLMStreamResponseDone(data: String): Boolean = "[DONE]" == data
    protected abstract fun decodeToLLMCompletionResponseInsideEdtAndCatching(body: String): LLMCompletionResponse
    protected abstract fun decodeToLLMChatResponseInsideEdtAndCatching(body: String): LLMChatResponse
    protected abstract fun decodeToLLMAgentResponseInsideEdtAndCatching(body: String): LLMAgentResponse

    interface LLMResponseListener<T : LLMChoice, R> {
        fun onDoneInsideEdtAndCatching(): R
        fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<T>)
    }

    abstract class LLMUsagesResponseListener<T : LLMChoice> : LLMResponseListener<T, String?> {
        private var displayUsage: String? = null
        override fun onDoneInsideEdtAndCatching(): String? = displayUsage
        override fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<T>) {
            llmResponse.usage?.takeIf { it.hasUsage() && (it.completion > RaccoonSettingsState.instance.candidates) }
                ?.getDisplayUsage()?.let { displayUsage = it }
        }
    }

    // run one llm request client job via okhttp3 synchronously, return done message if succeeded, otherwise throw exceptions
    private suspend fun <T : LLMChoice, R> runLLMJob(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmRequest: LLMRequest,
        decodeToLLMResponseInsideEdtAndCatching: (String) -> LLMResponse<T>,
        llmResponseListener: LLMResponseListener<T, R>
    ): R = ClientJobRunner(true).run {
        runClientJobWithStateTopic(llmRequest.action, isEnableNotify, projectForTopic, uiComponentForEdt) {
            llmToOkRequestInsideEdtAndCatching(llmRequest).let { okRequest ->
                if (llmRequest.isStream()) {
                    streamRequestInsideCatching(okRequest, uiComponentForEdt) { body ->
                        if (isLLMStreamResponseDone(body)) {
                            true
                        } else {
                            llmResponseListener.onResponseInsideEdtAndCatching(
                                decodeToLLMResponseInsideEdtAndCatching(body)
                            )
                            false
                        }
                    }
                } else {
                    llmResponseListener.onResponseInsideEdtAndCatching(
                        decodeToLLMResponseInsideEdtAndCatching(requestInsideEdtAndCatching(okRequest))
                    )
                }
                llmResponseListener.onDoneInsideEdtAndCatching()
            }
        }
    }

    suspend fun <R> runLLMCompletionJob(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmCompletionRequest: LLMCompletionRequest,
        llmCompletionListener: LLMResponseListener<LLMCompletionChoice, R>
    ): R = runLLMJob(
        isEnableNotify, projectForTopic, uiComponentForEdt, llmCompletionRequest,
        ::decodeToLLMCompletionResponseInsideEdtAndCatching, llmCompletionListener
    )

    suspend fun <R> runLLMChatJob(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmChatRequest: LLMChatRequest,
        llmChatListener: LLMResponseListener<LLMChatChoice, R>
    ): R = runLLMJob(
        isEnableNotify, projectForTopic, uiComponentForEdt, llmChatRequest,
        ::decodeToLLMChatResponseInsideEdtAndCatching, llmChatListener
    )


    companion object {
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 60L
        private const val WRITE_TIMEOUT_S = 30L
        val EMPTY_POST_REQUEST_BODY = "{}".toRequestBody()

        private val LOG = logger<LLMClient>()
    }
}
