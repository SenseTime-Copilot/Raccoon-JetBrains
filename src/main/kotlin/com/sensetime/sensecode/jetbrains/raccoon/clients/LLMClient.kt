package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.applyIf
import com.sensetime.sensecode.jetbrains.raccoon.clients.authorization.AuthenticatorBase
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChatResponse
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMCompletionResponse
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMUsage
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
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
    abstract val clientConfig: ClientConfig

    protected val apiBaseUrl: String
        get() = clientConfig.apiBaseUrl

    abstract val authenticator: AuthenticatorBase


    // request via okhttp3

    private val clientCoroutineScope: CoroutineScope =
        MainScope() + CoroutineName("${RaccoonPlugin.name}: ${this::class.simpleName}")
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS).build()
    private val sseEventSourceFactory: EventSource.Factory = EventSources.createFactory(okHttpClient)

    protected open fun onSuccessfulHeaderInsideCatching(headers: Headers) {}
    protected abstract fun throwLLMClientException(response: Response?, t: Throwable?): Nothing


    // client job

    private abstract class ClientJobRunner(
        isEnableDebugLog: Boolean
    ) {
        interface ClientJob<R> {
            suspend fun runClientJobInsideEdtAndCatching(): R
            fun onDoneInsideEdtAndCatching(requestR: R): String? = null
            fun onFailureInsideEdt(t: LLMClientException)
            fun onFinallyInsideEdt() {}
        }

        val id: Long = currentRequestID.getAndIncrement()
        val startTimeMs = RaccoonUtils.getSteadyTimestampMs()
        val debugLogger: Logger? = LOG.takeIf { isEnableDebugLog }

        companion object {
            private val currentRequestID: AtomicLong = AtomicLong(0L)
        }

        fun getCostS(): String =
            String.format("%.3fs", (RaccoonUtils.getSteadyTimestampMs() - startTimeMs) / 1000F)

        fun Project.getStateTopicPublisher(): RaccoonRequestStateListener =
            messageBus.syncPublisher(RACCOON_REQUEST_STATE_TOPIC)

        fun Request.printDebugLog(): Request = also { okRequest ->
            debugLogger?.debug { "Request[$id]: ${okRequest.method} ${okRequest.url}" }
            debugLogger?.debug { "Body[$id]: ${okRequest.body}" }
        }
    }

    private suspend fun <R> ClientJobRunner.runClientJob(
        action: String?,
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        isNeedNotifyGotoLogin: Boolean,
        clientJob: ClientJobRunner.ClientJob<R>
    ): R? = withContext(uiComponentForEdt?.let {
        Dispatchers.Main.immediate + ModalityState.stateForComponent(it).asContextElement()
    } ?: Dispatchers.Main.immediate) {
        RaccoonExceptions.resultOf({
            projectForTopic?.getStateTopicPublisher()?.onStartInsideEdtAndCatching(id, action)
            clientJob.runClientJobInsideEdtAndCatching().also { requestResult ->
                var doneMessage: String? = null
                try {
                    debugLogger?.trace { "Request[$id]: request result $requestResult" }
                    doneMessage = clientJob.onDoneInsideEdtAndCatching(requestResult)
                } finally {
                    projectForTopic?.getStateTopicPublisher()?.onDoneInsideEdtAndCatching(id, doneMessage)
                }
                debugLogger?.debug { "Request[$id]: Done, cost ${getCostS()}${doneMessage?.let { "\n\t$it" } ?: ""}" }
            }
        }, {
            // onFinally: To ensure that all code will be executed
            try {
                projectForTopic?.getStateTopicPublisher()?.onFinallyInsideEdt(id)
            } finally {
                clientJob.onFinallyInsideEdt()
            }
        }).onFailure { exceptionNotInMustRethrow ->
            // convert to LLMClientException
            ((exceptionNotInMustRethrow as? LLMClientException) ?: LLMClientMessageException(
                exceptionNotInMustRethrow.toString()
            )).let { llmClientException ->
                // To ensure that all code will be executed
                try {
                    LOG.warnWithDebug(llmClientException)
                    debugLogger?.debug { "Request[$id]: Failed($llmClientException), cost ${getCostS()}" }

                    if (llmClientException is LLMClientUnauthorizedException) {
                        authenticator.onUnauthorized()
                        if (isNeedNotifyGotoLogin) {
                            RaccoonNotification.notifyGotoLogin()
                        }
                    }
                } finally {
                    try {
                        projectForTopic?.getStateTopicPublisher()?.onFailureInsideEdt(id, llmClientException)
                    } finally {
                        clientJob.onFailureInsideEdt(llmClientException)
                    }
                }
            }
        }.getOrNull()
    }


    private suspend fun Request.requestInsideCatching(): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(this@requestInsideCatching)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(RaccoonExceptions.resultOf {
                        if (response.isSuccessful) {
                            onSuccessfulHeaderInsideCatching(response.headers)
                            requireNotNull(response.body).string()
                        } else {
                            throwLLMClientException(response, null)
                        }
                    })
                }
            })
            continuation.invokeOnCancellation {
                RaccoonExceptions.resultOf { call.cancel() }
            }
        }
    }

    private interface StreamResponseListener<R> {
        fun onSuccessfulBodyInsideCatching(body: String): Boolean
        fun onDoneInsideCatching(): R
    }

    private suspend fun <R> Request.streamRequestInsideCatching(
        responseListener: StreamResponseListener<R>
    ): R = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val eventSource =
                sseEventSourceFactory.newEventSource(this@streamRequestInsideCatching, object : EventSourceListener() {
                    private var isDone: Boolean = false
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        if (response.isSuccessful) {
                            onSuccessfulHeaderInsideCatching(response.headers)
                        } else {
                            throwLLMClientException(response, null)
                        }
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        if (isDone) {
                            throw LLMClientStreamException("receive data after DONE")
                        }
                        isDone = responseListener.onSuccessfulBodyInsideCatching(data)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        continuation.resumeWith(RaccoonExceptions.resultOf {
                            if (isDone) {
                                responseListener.onDoneInsideCatching()
                            } else {
                                throw LLMClientStreamException("stream not received DONE")
                            }
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

//    private fun <RequestR> ClientJobRunner.launchClientJob(
//        action: String?,
//        projectForTopic: Project?,
//        uiComponentForEdt: Component?,
//        isNeedNotifyGotoLogin: Boolean,
//        clientJob: ClientJobRunner.ClientJob<RequestR>
//    ): Job = clientCoroutineScope.launch(Dispatchers.Main.immediate) {
//        runClientJob(action, projectForTopic, uiComponentForEdt, isNeedNotifyGotoLogin, clientJob)
//    }


    private abstract class SingleRequestClientJob<RequestR> : ClientJobRunner.ClientJob<RequestR> {
        protected abstract suspend fun makeOkRequestInsideEdtAndCatching(): Request
        protected abstract suspend fun requestBlockInsideEdtAndCatching(okRequest: Request): RequestR
        override suspend fun runClientJobInsideEdtAndCatching(): RequestR =
            requestBlockInsideEdtAndCatching(makeOkRequestInsideEdtAndCatching().printDebugLog())
    }

    interface ResponseFailureListener {
        fun onFailureInsideEdt(t: LLMClientException)
    }

    interface ResponseFinallyListener {
        fun onFinallyInsideEdt() {}
    }


    private abstract class RequestRunner(
        isEnableDebugLog: Boolean
    ) : RequestRunnerBase(isEnableDebugLog) {
        override suspend fun requestBlockInsideEdtAndCatching(okRequest: Request) {
            TODO("Not yet implemented")
        }
    }

    private suspend fun <R> RequestRunnerBase.runClientJob(): R

    private interface ResponseListener<R> : ResponseDoneListener, ResponseFailureListener, ResponseFinallyListener {
        fun onSuccessfulBody(body: String): R
    }


    suspend fun <R> request(okRequest: Request): R =


    interface LLMDoneListener {
        fun onDoneInsideEdtAndCatching(finishReason: String?, usage: LLMUsage?) {}
    }

    interface LLMCompletionListener : LLMDoneListener, ResponseFailureListener, ResponseFinallyListener {
        fun onResponseInsideEdtAndCatching(llmCompletionResponse: LLMCompletionResponse)
    }

    private suspend fun requestLLM(
        isNeedNotifyGotoLogin: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
    ) {

    }

    fun requestLLMCompletion(
        isNeedNotifyGotoLogin: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmCompletionRequest: LLMCompletionRequest, llmCompletionListener: LLMCompletionListener
    ): Job?


    interface LLMChatListener : LLMDoneListener, ResponseFailureListener, ResponseFinallyListener {
        fun onResponseInsideEdtAndCatching(llmChatResponse: LLMChatResponse)
    }

    fun requestLLMChat(
        isNeedNotifyGotoLogin: Boolean,
        projectForTopic: Project,
        uiComponentForEdt: Component,
        llmChatRequest: LLMChatRequest, llmChatListener: LLMChatListener
    ): Job?


}


//import com.intellij.openapi.Disposable
//import com.intellij.openapi.diagnostic.Logger
//import com.intellij.openapi.diagnostic.debug
//import com.intellij.openapi.diagnostic.logger
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.util.Disposer
//import com.intellij.util.applyIf
//import com.sensetime.sensecode.jetbrains.raccoon.clients.authorization.AuthenticatorBase
//import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMAgentRequest
//import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
//import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
//import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChatResponse
//import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMCompletionResponse
//import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
//import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMUsage
//import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
//import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_CLIENT_REQUEST_STATE_TOPIC
//import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonClientRequestStateListener
//import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
//import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
//import com.sensetime.sensecode.jetbrains.raccoon.ui.common.invokeOnEdtSync
//import com.sensetime.sensecode.jetbrains.raccoon.utils.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.awaitClose
//import kotlinx.coroutines.channels.trySendBlocking
//import kotlinx.coroutines.flow.callbackFlow
//import kotlinx.coroutines.flow.collect
//import okhttp3.*
//import okhttp3.sse.EventSource
//import okhttp3.sse.EventSourceListener
//import okhttp3.sse.EventSources
//import java.awt.Component
//import java.io.IOException
//import java.util.*
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.AtomicBoolean
//import java.util.concurrent.atomic.AtomicLong
//import kotlin.coroutines.cancellation.CancellationException


abstract class LLMClient2 : Disposable {


    // CancellableJob

    interface CancellableJob {
        fun cancel()
    }

    private abstract class CancellableDisposableJob : CancellableJob, Disposable {
        val isDisposed: Boolean
            get() = _isDisposed.get()
        private val _isDisposed: AtomicBoolean = AtomicBoolean(false)

        protected abstract fun cancelInsideEdt()
        final override fun dispose() {
            RaccoonUIUtils.invokeOnEdtSync {
                cancelInsideEdt()
                _isDisposed.set(true)
            }
        }

        final override fun cancel() {
            Disposer.dispose(this)
        }
    }

    private class RequestJob(private val call: Call) : CancellableDisposableJob() {
        override fun cancelInsideEdt() {
            call.cancel()
        }
    }

    private class StreamRequestJob(private val event: EventSource) : CancellableDisposableJob() {
        override fun cancelInsideEdt() {
            event.cancel()
        }
    }


    // RequestRunner

    private interface ResponseDoneInfoGetter {
        fun getUsageInsideEdtAndCatching(): LLMUsage? = null
        fun getFinishReasonInsideEdtAndCatching(): String? = null
    }

    interface ResponseDoneListener {
        fun onDoneInsideEdtAndCatching() {}
    }

    interface ResponseFailureListener {
        fun onFailureInsideEdt(t: LLMClientException)
    }

    interface ResponseFinallyListener {
        fun onFinallyInsideEdt() {}
    }

    private interface ResponseStateListener : ResponseDoneInfoGetter, ResponseDoneListener, ResponseFailureListener,
        ResponseFinallyListener

    private abstract class RequestRunner(
        private val projectForTopic: Project?,
        private val uiComponentForEdt: Component?,
        private val responseStateListener: ResponseStateListener,
    ) {
        private enum class STATE { NOT_STARTED, RUNNING, DONE, FAILED }

        private data class SubRunner(
            val url: String, val stream: Boolean,
            val buildOkRequest: (Request.Builder, String) -> Request
        )

        private var state: STATE = STATE.NOT_STARTED
        private val isFinished: AtomicBoolean = AtomicBoolean(false)
        private val id: Long = currentRequestID.getAndIncrement()
        private val startTimeMs = RaccoonUtils.getSteadyTimestampMs()
        private var disposable: CancellableDisposableJob? = null
            set(value) {
                field?.cancel()
                field = value
            }

        companion object {
            private val currentRequestID: AtomicLong = AtomicLong(0L)
        }


        // call only once
        fun start(
            url: String, stream: Boolean, parent: Disposable,
            buildOkRequest: (Request.Builder, String) -> Request
        ): CancellableJob? =
            uiComponentForEdt.invokeOnEdtSync<CancellableJob?> {
                runCatchingWithOnFailureInsideEdt {
                    requireStateInsideEdt(STATE.NOT_STARTED, "start")
                    getStateTopicPublisher()?.onStartInsideEdtAndCatching(id)
                    startRequestAsync(
                        RaccoonUtils.getFormattedUTCDate().let { utcDate ->
                            buildOkRequest(
                                Request.Builder().url(url).header("Content-Type", "application/json")
                                    .addHeader("Date", utcDate)
                                    .applyIf(stream) { addHeader("Accept", "text/event-stream") }, utcDate
                            ).also { okRequest ->
                                debugLogger?.debug { "Request[$id]: ${okRequest.method} ${okRequest.url}" }
                                debugLogger?.debug { "Body[$id]: ${okRequest.body}" }
                            }
                        }).also { disposableJob ->
                        disposable = disposableJob
                        Disposer.register(parent, disposableJob)
                        state = STATE.RUNNING
                    }
                }.getOrNull()
            }


        private fun getCostS(): String =
            String.format("%.3fs", (RaccoonUtils.getSteadyTimestampMs() - startTimeMs) / 1000F)

        private fun getStateTopicPublisher(): RaccoonClientRequestStateListener? =
            projectForTopic?.messageBus?.syncPublisher(RACCOON_CLIENT_REQUEST_STATE_TOPIC)

        private fun requireStatesInsideEdt(expectedStates: Set<STATE>, actionName: String) {
            if (state !in expectedStates) {
                // must use FatalException for always rethrow
                throw RaccoonFatalException("Invalid state($state) for $actionName, expected $expectedStates")
            }
        }

        private fun requireStateInsideEdt(expectedState: STATE, actionName: String) {
            requireStatesInsideEdt(setOf(expectedState), actionName)
        }

        protected fun requireNotDisposed() {
            disposable?.takeIf { it.isDisposed }?.let { throw LLMClientCanceledException() }
        }

        protected fun <R> runCatchingWithOnFailureInsideEdt(block: () -> R): Result<R> =
            runCatching(block).onFailure(::toFailedStateInsideEdt)

        protected fun invokeOnEdtSync(block: () -> Unit) {
            uiComponentForEdt.invokeOnEdtSync(block)
        }
    }


    protected interface ResponseSuccessfulBodyListener<R> {
        fun onSuccessfulBodyInsideEdtAndCatching(body: String): R
    }


    private abstract class RequestRunner1<R>(
        isEnableDebugLog: Boolean,
        private val projectForTopic: Project?,
        private val uiComponentForEdt: Component?,
        private val responseListener: ResponseBaseListener<R>,
    ) {

        private val debugLogger: Logger? = LOG.takeIf { isEnableDebugLog }


        // must not catch any exceptions thrown inside callback(like finally)
        private fun toFinishedStateInsideEdt() {
            // must run only once
            require(!isFinished.getAndSet(true)) { "Already finished!" }
            // check state
            requireStatesInsideEdt(setOf(STATE.DONE, STATE.FAILED), "toFinishedState")

            // To ensure that all code will be executed but not catch any exceptions
            try {
                disposable = null
            } finally {
                try {
                    getStateTopicPublisher()?.onFinallyInsideEdt(id)
                } finally {
                    responseListener.onFinallyInsideEdt()
                }
            }
        }

        // must not catch any exceptions thrown inside callback(like catch)
        private fun toFailedStateInsideEdt(t: Throwable) {
            // use RaccoonExceptions.resultOf for filter must rethrow Exceptions
            RaccoonExceptions.resultOf({
                requireNotDisposed()
                requireStatesInsideEdt(setOf(STATE.NOT_STARTED, STATE.RUNNING), "toFailedState")
                state = STATE.FAILED
                throw t
            }, ::toFinishedStateInsideEdt).onFailure { exceptionNotInMustRethrow ->
                // only deal with Exceptions not in must rethrow
                ((exceptionNotInMustRethrow as? LLMClientException) ?: LLMClientMessageException(
                    exceptionNotInMustRethrow.toString()
                )).let { llmClientException ->
                    LOG.warnWithDebug(llmClientException)
                    debugLogger?.debug { "Request[$id]: Failed($llmClientException), cost ${getCostS()}" }

                    // To ensure that all code will be executed but not catch any exceptions
                    try {
                        getStateTopicPublisher()?.onFailureInsideEdt(id, llmClientException)
                    } finally {
                        responseListener.onFailureInsideEdt(llmClientException)
                    }
                }
            }
        }

        // must catch all exceptions thrown inside callback to onFailure(like try)
        protected fun toDoneStateInsideEdt() {
            runCatchingWithOnFailureInsideEdt {
                requireNotDisposed()
                requireStateInsideEdt(STATE.RUNNING, "toDoneState")

                // To ensure that all code will be executed but not catch any exceptions
                try {
                    val finishReason = responseListener.getFinishReasonInsideEdtAndCatching()
                    val usage = responseListener.getUsageInsideEdtAndCatching()
                    debugLogger?.debug { "Request[$id]: Done, cost ${getCostS()}, finishReason ${finishReason}\n\tUsage: ${usage}" }
                    getStateTopicPublisher()?.onDoneInsideEdtAndCatching(id, finishReason, usage)
                } finally {
                    responseListener.onDoneInsideEdtAndCatching()
                }
                state = STATE.DONE
            }.onSuccess { toFinishedStateInsideEdt() }
        }

        // must catch all exceptions thrown inside callback to onFailure(like try)
        protected fun onSuccessfulBodyInsideEdtAndCatching(body: String): R {
            requireNotDisposed()
            requireStateInsideEdt(STATE.RUNNING, "onSuccessfulBody")
            debugLogger?.debug { "Response[$id]: $body" }
            return responseListener.onSuccessfulBodyInsideEdtAndCatching(body)
        }

        protected abstract fun startRequestAsync(okRequest: Request): CancellableDisposableJob


    }


    // request functions

    protected abstract suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder

    protected interface ResponseListener : ResponseBaseListener<Unit>


    interface RequestHandler<R> {
        val url: String
        val isEnableDebugLog: Boolean
        fun buildOkRequest(requestBuilder: Request.Builder, formattedUTCDate: String): Request
        fun onSuccessfulBodyInsideEdtAndCatching(body: String): R
        fun <N> onDoneInsideEdtAndCatching(): RequestHandler<N>?
    }

    private fun request(
        url: String,
        isEnableDebugLog: Boolean,
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        responseListener: ResponseListener,
        buildOkRequest: (Request.Builder, String) -> Request
    ): CancellableJob? =
        object : RequestRunner<Unit>(isEnableDebugLog, projectForTopic, uiComponentForEdt, responseListener) {
            override fun startRequestAsync(okRequest: Request): CancellableDisposableJob =
                RequestJob(okHttpClient.newCall(okRequest).apply {
                    enqueue(object : Callback {
                        override fun onResponse(call: Call, response: Response) {
                            invokeOnEdtSync {
                                runCatchingWithOnFailureInsideEdt {
                                    if (response.isSuccessful) {
                                        onSuccessfulHeaderInsideEdtAndCatching(response.headers)
                                        onSuccessfulBodyInsideEdtAndCatching(requireNotNull(response.body).string())
                                    } else {
                                        throwLLMClientException(response, null)
                                    }
                                }.onSuccess { toDoneStateInsideEdt() }
                            }
                        }

                        override fun onFailure(call: Call, e: IOException) {
                            invokeOnEdtSync { runCatchingWithOnFailureInsideEdt { throw e } }
                        }
                    })
                })
        }.start(url, false, this, buildOkRequest)

    protected fun requestWithAuth(
        url: String,
        isEnableDebugLog: Boolean,
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        responseListener: ResponseListener,
        buildOkRequestAfterAuth: (Request.Builder, String) -> Request
    ): CancellableJob? = request(url, isEnableDebugLog, projectForTopic, uiComponentForEdt, responseListener) {

    }

    protected interface StreamResponseListener : ResponseBaseListener<Boolean>

    private fun requestStreamWithAuth(
        url: String,
        isEnableDebugLog: Boolean,
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        responseListener: StreamResponseListener,
        buildOkRequest: (Request.Builder, String) -> Request
    ): CancellableJob? =
        object : RequestRunner<Boolean>(isEnableDebugLog, projectForTopic, uiComponentForEdt, responseListener) {
            override fun startRequestAsync(okRequest: Request): CancellableDisposableJob = StreamRequestJob(
                sseEventSourceFactory.newEventSource(okRequest, object : EventSourceListener() {
                    private var isDone: Boolean = false
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        invokeOnEdtSync {
                            if (response.isSuccessful) {
                                onSuccessfulHeaderInsideEdtAndCatching(response.headers)
                            } else {
                                throwLLMClientException(response, null)
                            }
                        }
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        // must not catch any exceptions, because of EventSource will catch it to onFailure
                        invokeOnEdtSync {
                            if (isDone) {
                                throw LLMClientStreamException("receive data after DONE")
                            }
                            isDone = onSuccessfulBodyInsideEdtAndCatching(data)
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        invokeOnEdtSync {
                            if (isDone) {
                                toDoneStateInsideEdt()
                            } else {
                                runCatchingWithOnFailureInsideEdt { throw LLMClientStreamException("stream not received DONE") }
                            }
                        }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        invokeOnEdtSync { runCatchingWithOnFailureInsideEdt { throwLLMClientException(response, t) } }
                    }
                })
            )
        }.start(url, true, this, buildOkRequest)


    // LLM requests

    protected abstract fun Request.Builder.buildCompletionRequest(completionRequest: LLMCompletionRequest)
    protected abstract fun toLLMResponseInsideEdtAndCatching(body: String): LLMResponse

    interface LLMSuccessfulResponseListener {
        fun onLLMResponseInsideEdtAndCatching(llmResponse: LLMResponse)
    }

    interface LLMResponseListener : ResponseFailureListener, ResponseFinallyListener, LLMSuccessfulResponseListener


    fun requestCompletion(
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        completionRequest: LLMCompletionRequest,
        responseListener: LLMResponseListener,
    ): CancellableJob?


    fun requestLLM(
        projectForTopic: Project?,
        uiComponentForEdt: Component?,
        responseListener: LLMResponseListener
    ): CancellableJob? = request(
        true,
        projectForTopic,
        uiComponentForEdt,
        object : ResponseListener, ResponseFailureListener by responseListener,
            ResponseFinallyListener by responseListener {
            override fun onSuccessfulBodyInsideEdtAndCatching(body: String) {
                TODO("Not yet implemented")
            }

            override fun getFinishReasonInsideEdtAndCatching(): String? {
                return super.getFinishReasonInsideEdtAndCatching()
            }

            override fun getUsageInsideEdtAndCatching(): LLMUsage? {
                return super.getUsageInsideEdtAndCatching()
            }

            override fun onDoneInsideEdtAndCatching() {
                super.onDoneInsideEdtAndCatching()
            }
        }) {}


    // sensitive
    open suspend fun getSensitiveConversations(
        startTime: String, endTime: String? = null
    ): Map<String, RaccoonSensitiveListener.SensitiveConversation> = emptyMap()

    companion object {
        private const val CONNECT_TIMEOUT_S = 5L
        private const val READ_TIMEOUT_S = 60L
        private const val WRITE_TIMEOUT_S = 30L

        private val LOG = logger<LLMClient>()
    }
}


abstract class LLMClient1 : Disposable {


    private val clientCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName(this::class.simpleName!!))

    fun launchRequest2(isPublishState: Boolean = false, okRequest: Request, responseListener: ResponseListener): Job =
        clientCoroutineScope.launch {
            val id = currentRequestID.takeIf { isPublishState }?.getAndIncrement()
            try {
                id?.let { requestStateTopicPublisher.onStart(it) }
                block(okRequest, id)
            } catch (e: Throwable) {
                if (e is UnauthorizedException) {
                    RaccoonUIUtils.invokeOnUIThreadLater { authenticator.onUnauthorized() }
                }
                id?.takeIf { e !is CancellationException }
                    ?.let { requestStateTopicPublisher.onError(it, e.localizedMessage) }
                throw e
            } finally {
                id?.let { requestStateTopicPublisher.onFinally(it) }
            }
        }


    fun launchRequest(
        isPublishState: Boolean = false,
        okRequest: Request,
        block: suspend (Request, Long?) -> Unit
    ): Job = clientCoroutineScope.launch {
        val id = currentRequestID.takeIf { isPublishState }?.getAndIncrement()
        try {
            id?.let { requestStateTopicPublisher.onStart(it) }
            block(okRequest, id)
        } catch (e: Throwable) {
            if (e is UnauthorizedException) {
                RaccoonUIUtils.invokeOnUIThreadLater { authenticator.onUnauthorized() }
            }
            id?.takeIf { e !is CancellationException }
                ?.let { requestStateTopicPublisher.onError(it, e.localizedMessage) }
            throw e
        } finally {
            id?.let { requestStateTopicPublisher.onFinally(it) }
        }
    }

    fun <Resp, Result> runRequest(
        okRequest: Request,
        requestBlock: suspend (Request) -> Resp,
        block: (Resp) -> Result
    ): Result {

    }

    private suspend inline fun <R> runRequestWrapper(isPublishState: Boolean = false, block: (Long?) -> R): R {
        val id = currentRequestID.takeIf { isPublishState }?.getAndIncrement()
        return try {
            id?.let { requestStateTopicPublisher.onStart(it) }
            block(id)
        } catch (e: Throwable) {
            if (e is UnauthorizedException) {
                RaccoonUIUtils.invokeOnUIThreadLater { authenticator.onUnauthorized() }
            }
            id?.takeIf { e !is CancellationException }
                ?.let { requestStateTopicPublisher.onError(it, e.localizedMessage) }
            throw e
        } finally {
            id?.let { requestStateTopicPublisher.onFinally(it) }
        }
    }


    private suspend fun <R> runRequestWrapper1(
        request: CodeRequest,
        stream: Boolean,
        block: suspend (Long, Request) -> R
    ): R {
        val id = currentRequestID.getAndIncrement()
        return try {
            try {
                requestStateTopicPublisher.onStart(id)
                block(id, toOkHttpRequest(request, stream))
            } catch (e: CancellationException) {
                throw (e.cause as? CodeClientException) ?: e
            }
        } catch (e: Throwable) {
            if (e is UnauthorizedException) {
                if (isSupportLogin) {
                    logout()
                }
                RaccoonUIUtils.invokeOnUIThreadLater { RaccoonNotification.notifyGotoLogin() }
            }
            if (e !is CancellationException) {
                requestStateTopicPublisher.onError(id, e.localizedMessage)
            }
            throw e
        } finally {
            requestStateTopicPublisher.onFinally(id)
        }
    }

    fun release() {
        clientCoroutineScope.cancel()
    }


    interface ClientApi {
        fun addRequestBody(requestBuilder: Request.Builder, request: CodeRequest, stream: Boolean): Request.Builder
        fun toCodeResponse(body: String, stream: Boolean): CodeResponse
    }
    )


    protected abstract fun getClientApi(apiPath: String): ClientApi


    protected abstract suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder

    protected open suspend fun toOkHttpRequest(request: CodeRequest, stream: Boolean): Request {
        val utcDate: String = getUTCDate()
        var requestBuilder =
            Request.Builder().url(getApiEndpoint(request.apiPath)).header("Content-Type", "application/json")
                .addHeader("Date", utcDate)
        if (stream) {
            requestBuilder = requestBuilder.addHeader("Accept", "text/event-stream")
        }
        requestBuilder = addAuthorization(requestBuilder, utcDate)
        return getClientApi(request.apiPath).addRequestBody(requestBuilder, request, stream).build()
    }

    protected open fun toCodeResponse(apiPath: String, body: String, stream: Boolean): CodeResponse =
        getClientApi(apiPath).toCodeResponse(body, stream)

    protected fun toErrorException(
        response: Response? = null,
        t: Throwable? = null,
        bodyErrorGetter: (Response?) -> String?
    ): Throwable {
        try {
            val httpCode = response?.code?.let { "Http code: $it" }
            val clientResponse = response?.takeUnless { it.code in 500..599 }
            return listOfNotNull(
                bodyErrorGetter(clientResponse)?.letIfNotBlank { "Details: $it" },
                t?.let { "Exception: ${it.localizedMessage}" }).ifEmpty {
                listOfNotNull(
                    httpCode,
                    "Message: ${clientResponse?.message.ifNullOrBlank(Error.UNKNOWN_ERROR)}"
                )
            }.joinToString("\n")
                .let { if (401 == response?.code) UnauthorizedException(it) else CodeClientException(it) }
        } catch (e: Throwable) {
            return if (e is CodeClientException) {
                e
            } else {
                CodeClientException(e.localizedMessage)
            }
        }
    }

    protected fun toErrorException(
        apiPath: String,
        stream: Boolean,
        response: Response? = null,
        t: Throwable? = null
    ): Throwable = toErrorException(response, t) { clientResponse ->
        clientResponse?.body?.string()?.let {
            try {
                toCodeResponse(apiPath, it, stream).error?.getShowError()
            } catch (e: Throwable) {
                if (e is SensitiveException) {
                    throw e
                } else {
                    "Exception: ${e.localizedMessage}"
                }
            }
        }
    }

    open fun onOkResponse(response: Response) {
    }

    suspend fun request(request: CodeRequest): CodeResponse = runRequestWrapper(request, false) { id, okHttpRequest ->
        okHttpClient.newCall(okHttpRequest).await().let { response ->
            response.takeIf { it.isSuccessful }?.body?.let {
                onOkResponse(response)
                toCodeResponse(request.apiPath, it.string(), false).also { codeResponse ->
                    requestStateTopicPublisher.onDone(id, codeResponse.usage)
                }
            } ?: throw toErrorException(request.apiPath, false, response)
        }
    }

    protected open fun isStreamResponseDone(data: String): Boolean = "[DONE]" == data

    suspend fun requestStream(request: CodeRequest, onStreamResponse: (CodeStreamResponse) -> Unit) =
        runRequestWrapper(request, true) { requestID, okHttpRequest ->
            callbackFlow {
                val eventSource = sseEventSourceFactory.newEventSource(okHttpRequest, object : EventSourceListener() {
                    private var openResponse: Response? = null
                    private var usage: Usage? = null

                    override fun onOpen(eventSource: EventSource, response: Response) {
                        super.onOpen(eventSource, response)

                        openResponse = response
                        onOkResponse(response)
                        trySendBlocking(CodeStreamResponse.Connected)
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        super.onEvent(eventSource, id, type, data)

                        openResponse = null
                        if (isStreamResponseDone(data)) {
                            trySendBlocking(CodeStreamResponse.Done)
                            requestStateTopicPublisher.onDone(requestID, usage)
                        } else {
                            toCodeResponse(request.apiPath, data, true).toStreamResponse().forEach {
                                if (it is CodeStreamResponse.TokenUsage) {
                                    usage = it.usage
                                }
                                trySendBlocking(it)
                            }
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        super.onClosed(eventSource)

                        trySendBlocking(CodeStreamResponse.Closed)
                        channel.close()

                        openResponse?.let {
                            cancel("RaccoonClient", toErrorException(request.apiPath, true, it))
                        }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        super.onFailure(eventSource, t, response)

                        openResponse = null
                        cancel("RaccoonClient", toErrorException(request.apiPath, true, response, t))
                    }
                })

                awaitClose {
                    eventSource.cancel()
                }
            }.collect {
                onStreamResponse(it)
            }
        }

//    companion object {
//        @JvmStatic
//        fun getUTCDate(): String {
//            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
//            sdf.timeZone = TimeZone.getTimeZone("UTC")
//            return sdf.format(Date())
//        }
//
//        private val currentRequestID: AtomicLong = AtomicLong(0L)
//    }
}

