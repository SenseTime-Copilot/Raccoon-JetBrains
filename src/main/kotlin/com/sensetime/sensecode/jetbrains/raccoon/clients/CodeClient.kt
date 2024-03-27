package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.BehaviorMetrics
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.CodeResponse
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.CodeStreamResponse
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.Error
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.Usage
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonClientRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })

        continuation.invokeOnCancellation {
            // Ignore cancel exception
            runCatching { cancel() }.onFailure { e ->
                if (e is CancellationException) {
                    throw e
                }
            }
        }
    }

abstract class CodeClient {
    abstract val name: String
    protected val baseUrl: String
        get() = RaccoonSettingsState.instance.clientBaseUrlMap.getValue(name)
    open val webBaseUrl: String? = null
    protected fun getApiEndpoint(apiPath: String) = baseUrl + apiPath


    // authorization

    abstract val userName: String?
    open val alreadyLoggedIn: Boolean = false
    open val isSupportLogin: Boolean = false

    open suspend fun login(nationCode: String, phone: String, password: CharArray) {
        throw NotImplementedError("")
    }

    open suspend fun login(email: String, password: CharArray) {
        throw NotImplementedError("")
    }

    open suspend fun logout() {
        throw NotImplementedError("")
    }

    data class AkSkSettingsItem(
        val label: String,
        val toolTipText: String?,
        val getter: () -> String,
        val setter: (String) -> Unit
    )

    data class AkSkSettings(
        val groupTitle: String,
        val groupComment: String?,
        val akItem: AkSkSettingsItem?,
        val skItem: AkSkSettingsItem
    )

    open fun getAkSkSettings(): AkSkSettings? = null


    // sensitive
    open suspend fun getSensitiveConversations(
        startTime: String, endTime: String? = null, action: String
    ): Map<String, RaccoonSensitiveListener.SensitiveConversation> = emptyMap()

    abstract suspend fun uploadBehaviorMetrics(behaviorMetrics: BehaviorMetrics): Boolean

    // request via okhttp3

    interface ClientApi {
        fun addRequestBody(requestBuilder: Request.Builder, request: CodeRequest, stream: Boolean): Request.Builder
        fun toCodeResponse(body: String, stream: Boolean): CodeResponse
    }

    open class CodeClientException(message: String) : Exception(message)

    class UnauthorizedException(message: String? = null) :
        CodeClientException("Unauthorized${message.ifNullOrBlankElse("!") { ": $it" }}")

    class SensitiveException(message: String = "") : CodeClientException(message)

    private val requestStateTopicPublisher: RaccoonClientRequestStateListener
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC)

    protected val okHttpClient: OkHttpClient =
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).build()
    private val sseEventSourceFactory: EventSource.Factory = EventSources.createFactory(okHttpClient)

    protected abstract fun getClientApi(apiPath: String): ClientApi

    private suspend fun <R> runRequestWrapper(
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
                RaccoonUIUtils.invokeOnUIThreadLater { RaccoonNotification.notifyGotoLogin(true) }
            }
            if (e !is CancellationException) {
                requestStateTopicPublisher.onError(id, e.localizedMessage)
            }
            throw e
        } finally {
            requestStateTopicPublisher.onFinally(id)
        }
    }

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

    companion object {
        @JvmStatic
        fun getUTCDate(): String {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        private val currentRequestID: AtomicLong = AtomicLong(0L)
    }
}