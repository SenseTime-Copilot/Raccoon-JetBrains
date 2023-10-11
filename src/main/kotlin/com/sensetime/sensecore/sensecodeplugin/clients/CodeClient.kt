package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.*
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_CLIENTS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
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
            runCatching { cancel() }
        }
    }

abstract class CodeClient {
    abstract val name: String

    abstract val userName: String?
    open val alreadyLoggedIn: Boolean = false
    open val isSupportLogin: Boolean = false
    open suspend fun login(apiEndpoint: String) {
        throw NotImplementedError("")
    }

    open suspend fun logout(apiEndpoint: String) {
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

    class UnauthorizedException(clientName: String? = null, details: String? = null) :
        Exception("Code client${if (clientName.isNullOrBlank()) "" else "($clientName)"} unauthorized${if (details.isNullOrBlank()) "!" else ": $details"}")

    suspend fun request(request: CodeRequest): CodeResponse = try {
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC).onStart()
        client.newCall(toOkHttpRequest(request, false)).await()
            .let { response ->
                response.takeIf { it.isSuccessful }?.body?.let {
                    toCodeResponse(it.string(), false).apply {
                        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC)
                            .onDone(usage)
                    }
                } ?: throw IOException(toErrorMessage(false, response).apply {
                    response.code.takeIf { 401 == it }?.let {
                        SenseCodeNotification.notifyLoginWithSettingsAction()
                    }
                })
            }
    } catch (e: Throwable) {
        if (e is UnauthorizedException) {
            SenseCodeNotification.notifyLoginWithSettingsAction()
        }
        if (e !is CancellationException) {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC)
                .onError(e.localizedMessage)
        }
        throw e
    } finally {
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC).onFinally()
    }

    fun requestStream(request: CodeRequest): Flow<CodeStreamResponse> = callbackFlow {
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC).onStart()
        val eventSource = factory.newEventSource(toOkHttpRequest(request, true), object : EventSourceListener() {
            private var openResponse: Response? = null
            private var usage: Usage? = null

            override fun onOpen(eventSource: EventSource, response: Response) {
                super.onOpen(eventSource, response)

                openResponse = response
                trySendBlocking(CodeStreamResponse.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                super.onEvent(eventSource, id, type, data)

                openResponse = null
                if (isStreamResponseDone(data)) {
                    trySendBlocking(CodeStreamResponse.Done)
                    ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC)
                        .onDone(usage)
                } else {
                    toCodeResponse(data, true).toStreamResponse().forEach {
                        if (it is CodeStreamResponse.TokenUsage) {
                            usage = it.usage
                        }
                        trySendBlocking(it)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                super.onClosed(eventSource)

                openResponse?.let {
                    // onClosed after a onOpen, maybe an error response without stream
                    trySendBlocking(CodeStreamResponse.Error(toErrorMessage(true, it)))
                }
                trySendBlocking(CodeStreamResponse.Closed)
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                super.onFailure(eventSource, t, response)

                openResponse = null
                val errorMessage = toErrorMessage(true, response, t)
                trySendBlocking(CodeStreamResponse.Error(errorMessage))
                response?.code?.takeIf { 401 == it }?.let {
                    SenseCodeNotification.notifyLoginWithSettingsAction()
                }
                ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC)
                    .onError(errorMessage)
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }.onCompletion {
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC).onFinally()
    }.catch { e ->
        if (e is UnauthorizedException) {
            SenseCodeNotification.notifyLoginWithSettingsAction()
        }
        if (e !is CancellationException) {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC)
                .onError(e.localizedMessage)
        }
        throw e
    }

    protected val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()
    private val factory = EventSources.createFactory(client)

    protected abstract suspend fun addAuthorization(
        requestBuilder: Request.Builder,
        apiEndpoint: String,
        utcDate: String
    ): Request.Builder

    protected abstract fun addPostBody(
        requestBuilder: Request.Builder,
        request: CodeRequest,
        stream: Boolean
    ): Request.Builder

    protected open suspend fun toOkHttpRequest(request: CodeRequest, stream: Boolean): Request {
        val utcDate: String = getUTCDate()
        var requestBuilder = Request.Builder()
            .url(request.apiEndpoint)
            .header("Content-Type", "application/json")
            .addHeader("Date", utcDate)
        if (stream) {
            requestBuilder = requestBuilder.addHeader("Accept", "text/event-stream")
        }
        requestBuilder = addAuthorization(requestBuilder, request.apiEndpoint, utcDate)
        return addPostBody(requestBuilder, request, stream).build()
    }

    protected open fun isStreamResponseDone(data: String): Boolean = "[DONE]" == data
    protected abstract fun toCodeResponse(body: String, stream: Boolean): CodeResponse
    protected open fun toErrorMessage(stream: Boolean, response: Response? = null, t: Throwable? = null): String {
        val httpCode = response?.code?.let { "Http code: $it" }
        val clientResponse = response?.takeUnless { it.code in 500..599 }
        return listOfNotNull(
            httpCode,
            clientResponse?.message?.takeIf { it.isNotBlank() }?.let { "Message: $it" },
            "Details: ${
                clientResponse?.body?.string()?.let {
                    kotlin.runCatching { toCodeResponse(it, stream).error?.getShowError() }
                        .getOrElse { "Exception: ${it.localizedMessage}" }
                } ?: Error.UNKNOWN_ERROR
            }",
            t?.let { "Exception: ${it.localizedMessage}" }).joinToString("\n")
    }

    companion object {
        @JvmStatic
        fun getUTCDate(): String {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}