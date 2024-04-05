package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.applyIf
import com.sensetime.sensecode.jetbrains.raccoon.clients.authorization.AuthenticatorBase
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMAgentRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnEdtSync
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.toCoroutineContext
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.plusIfNotNull
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.awt.Component
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException


internal abstract class LLMClient : Disposable {
    // common

    abstract val name: String
    protected abstract val clientConfig: ClientConfig

    private val apiBaseUrl: String
        get() = clientConfig.apiBaseUrl

    abstract val authenticator: AuthenticatorBase


    // client job: contains one or more requests, corresponding to a state transition

    private class ClientJobRunner(
        isEnableDebugLog: Boolean
    ) {
        companion object {
            private val currentRequestID: AtomicLong = AtomicLong(0L)
        }

        val id: Long = currentRequestID.getAndIncrement()
        val startTimeMs = RaccoonUtils.getSteadyTimestampMs()
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
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = try {
        clientJobInsideEdtAndCatching().also { result ->
            debugLogger?.debug { "Request[$id]: Done, cost ${getCostS()}. \"$result\"" }
        }
    } catch (t: Throwable) {
        LOG.warnWithDebug(t)
        debugLogger?.debug { "Request[$id]: Failed, cost ${getCostS()}. \"$t\"" }
        if (t is LLMClientUnauthorizedException) {
            authenticator.onUnauthorizedInsideEdt(isEnableNotify)
        }
        throw t
    }

    // run a client job synchronously, return R if succeeded, otherwise throw exceptions
    private suspend fun <R> ClientJobRunner.runClientJob(
        isEnableNotify: Boolean,
        uiComponentForEdt: Component?,
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = withContext(Dispatchers.Main.immediate.plusIfNotNull(uiComponentForEdt?.toCoroutineContext())) {
        runClientJobInsideEdt(isEnableNotify, clientJobInsideEdtAndCatching)
    }

    // run a client job with publish state topic synchronously, return R if succeeded, otherwise throw exceptions
    private suspend fun <R> ClientJobRunner.runClientJobWithStateTopic(
        action: String?, isEnableNotify: Boolean,
        projectForTopic: Project, uiComponentForEdt: Component?,
        clientJobInsideEdtAndCatching: suspend () -> R
    ): R = withContext(Dispatchers.Main.immediate.plusIfNotNull(uiComponentForEdt?.toCoroutineContext())) {
        try {
            projectForTopic.publishStateTopicWithCatching { onStartInsideEdtAndCatching(id, action) }
            runClientJobInsideEdt(isEnableNotify) {
                clientJobInsideEdtAndCatching().also { result ->
                    projectForTopic.publishStateTopicWithCatching {
                        onDoneInsideEdtAndCatching(id, result?.let { "$it" })
                    }
                    debugLogger?.debug { "Request[$id]: done message is \"$result\"" }
                }
            }
        } catch (t: Throwable) {
            projectForTopic.publishStateTopicWithCatching { onFailureInsideEdtAndCatching(id, t) }
            throw t
        } finally {
            projectForTopic.publishStateTopicWithCatching { onFinallyInsideEdtAndCatching(id) }
        }
    }


    // request via okhttp3

    private val clientCoroutineScope: CoroutineScope =
        MainScope() + CoroutineName("${RaccoonPlugin.name}: ${this::class.simpleName}")
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS).build()
    private val sseEventSourceFactory: EventSource.Factory = EventSources.createFactory(okHttpClient)

    protected open fun onSuccessfulHeaderInsideEdtAndCatching(headers: Headers) {}
    protected abstract fun throwLLMClientException(response: Response?, t: Throwable?): Nothing

    // return body if succeeded, otherwise throw exceptions
    private suspend fun ClientJobRunner.requestInsideEdtAndCatching(okRequest: Request): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = okHttpClient.newCall(okRequest.printIfEnableDebugLog())
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            response.takeIf { it.isSuccessful } ?: throwLLMClientException(response, null)
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
                    private var result: R? = null
                        get() = field?.takeIf { (it as? Boolean) != false }

                    override fun onOpen(eventSource: EventSource, response: Response) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        if (response.isSuccessful) {
                            uiComponentForEdt.invokeOnEdtSync {
                                onSuccessfulHeaderInsideEdtAndCatching(response.headers)
                            }
                        } else {
                            throwLLMClientException(response, null)
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
                            result ?: throw LLMClientStreamException("stream not received DONE")
                        })
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            throwLLMClientException(response, t)
                        })
                    }
                })
            continuation.invokeOnCancellation {
                RaccoonExceptions.resultOf { eventSource.cancel() }
            }
        }
    }


    // run one request client job via okhttp3 synchronously, return R if succeeded, otherwise throw exceptions
    protected suspend fun <R> Request.request(
        isEnableNotify: Boolean,
        isEnableDebugLog: Boolean,
        uiComponentForEdt: Component?,
        onResponseInsideEdtAndCatching: (String) -> R
    ): R = ClientJobRunner(isEnableDebugLog).run {
        runClientJob(isEnableNotify, uiComponentForEdt) {
            onResponseInsideEdtAndCatching(requestInsideEdtAndCatching(this@request))
        }
    }


    // LLM request

    protected abstract suspend fun Request.Builder.addAuthorizationHeaderInsideEdtAndCatching(): Request.Builder
    protected abstract fun Request.Builder.addLLMBodyInsideEdtAndCatching(llmRequest: LLMRequest): Request.Builder

    private fun LLMRequest.getApiEndpointFromConfig(): String = when (this) {
        is LLMAgentRequest -> clientConfig.getAgentApiEndpoint()
        is LLMChatRequest -> clientConfig.getChatApiEndpoint()
        is LLMCompletionRequest -> clientConfig.getCompletionApiEndpoint()
    }

    private suspend fun LLMRequest.toOkRequestInsideEdtAndCatching(): Request =
        createRequestBuilderWithCommonHeader(getApiEndpointFromConfig()).addAuthorizationHeaderInsideEdtAndCatching()
            .addLLMBodyInsideEdtAndCatching(this).build()

    private fun isLLMStreamResponseDone(data: String): Boolean = "[DONE]" == data
    protected abstract fun <T : LLMChoice> decodeToLLMResponseInsideEdtAndCatching(body: String): LLMResponse<T>

    // run one llm request client job via okhttp3 synchronously, return done message if succeeded, otherwise throw exceptions
    private suspend fun <T : LLMChoice> LLMRequest.requestLLM(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        onResponseInsideEdtAndCatching: (LLMResponse<T>) -> Unit
    ): String? = ClientJobRunner(true).run {
        runClientJobWithStateTopic(action, isEnableNotify, projectForTopic, uiComponentForEdt) {
            toOkRequestInsideEdtAndCatching().let { okRequest ->
                // return done message
                val onResponseBodyInsideEdtAndCatching: (String) -> String? = { body ->
                    val llmResponse = decodeToLLMResponseInsideEdtAndCatching<T>(body)
                    onResponseInsideEdtAndCatching(llmResponse)
                    llmResponse.usage?.takeIf { it.hasUsage() }?.getDisplayUsage()
                }
                if (isStream()) {
                    var message: String? = null
                    streamRequestInsideCatching(okRequest, uiComponentForEdt) { body ->
                        if (isLLMStreamResponseDone(body)) {
                            true
                        } else {
                            onResponseBodyInsideEdtAndCatching(body)?.let { message = it }
                            false
                        }
                    }
                    message
                } else {
                    onResponseBodyInsideEdtAndCatching(requestInsideEdtAndCatching(okRequest))
                }
            }
        }
    }

    private fun launchClientJob(blockInsideEdt: suspend CoroutineScope.() -> Unit): Job =
        clientCoroutineScope.launch(Dispatchers.Main.immediate, block = blockInsideEdt)

    interface ClientJobStateListener {
        fun onFailureInsideEdt(t: Throwable)
        fun onFinallyInsideEdt() {}
    }

    private fun launchClientJobWithCatching(
        clientJobStateListener: ClientJobStateListener,
        blockInsideEdtAndCatching: suspend CoroutineScope.() -> Unit
    ): Job = launchClientJob {
        RaccoonExceptions.resultOf(
            { blockInsideEdtAndCatching() }, clientJobStateListener::onFinallyInsideEdt
        ).onFailure(clientJobStateListener::onFailureInsideEdt)
    }


    interface LLMListener<T : LLMChoice> : ClientJobStateListener {
        fun onDoneInsideEdtAndCatching()
        fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<T>)
    }

    private fun <T : LLMChoice> requestLLM(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmRequest: LLMRequest,
        llmListener: LLMListener<T>
    ): Job = launchClientJobWithCatching(llmListener) {
        llmRequest.requestLLM(
            isEnableNotify, projectForTopic, uiComponentForEdt,
            llmListener::onResponseInsideEdtAndCatching
        )
        llmListener.onDoneInsideEdtAndCatching()
    }

    fun requestLLMCompletion(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmCompletionRequest: LLMCompletionRequest,
        llmCompletionListener: LLMListener<LLMCompletionChoice>
    ): Job = requestLLM(isEnableNotify, projectForTopic, uiComponentForEdt, llmCompletionRequest, llmCompletionListener)

    fun requestLLMChat(
        isEnableNotify: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmChatRequest: LLMChatRequest,
        llmChatListener: LLMListener<LLMChatChoice>
    ): Job = requestLLM(isEnableNotify, projectForTopic, uiComponentForEdt, llmChatRequest, llmChatListener)


    override fun dispose() {
        clientCoroutineScope.cancel()
    }

    companion object {
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 60L
        private const val WRITE_TIMEOUT_S = 30L

        private val LOG = logger<LLMClient>()

        @JvmStatic
        private fun createRequestBuilderWithCommonHeader(
            apiEndpoint: String,
            stream: Boolean = false
        ): Request.Builder = Request.Builder().url(apiEndpoint).header("Content-Type", "application/json")
            .addHeader("Date", RaccoonUtils.getFormattedUTCDate())
            .applyIf(stream) { addHeader("Accept", "text/event-stream") }
    }
}
