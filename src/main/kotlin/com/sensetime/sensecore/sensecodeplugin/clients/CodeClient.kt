package com.sensetime.sensecore.sensecodeplugin.clients

import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    abstract suspend fun login(apiEndpoint: String)
    abstract suspend fun logout(apiEndpoint: String)

    class UnauthorizedException(clientName: String? = null, details: String? = null) :
        Exception("Code client${if (clientName.isNullOrBlank()) "" else "($clientName)"} unauthorized${if (details.isNullOrBlank()) "!" else ": $details"}")

    suspend fun request(request: CodeRequest): CodeResponse = client.newCall(toOkHttpRequest(request, false)).await()
        .let { response ->
            response.takeIf { it.isSuccessful }?.body?.let { toCodeResponse(it.string(), false) } ?: throw IOException(
                toErrorMessage(false, response)
            )
        }

    fun requestStream(request: CodeRequest): Flow<CodeStreamResponse> = callbackFlow {
        val eventSource = factory.newEventSource(toOkHttpRequest(request, true), object : EventSourceListener() {
            private var openResponse: Response? = null

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
                } else {
                    toCodeResponse(data, true).toStreamResponse().forEach {
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
                trySendBlocking(CodeStreamResponse.Error(toErrorMessage(true, response, t)))
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }


    protected val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()
    private val factory = EventSources.createFactory(client)

    protected abstract suspend fun addAuthorization(
        requestBuilder: Request.Builder,
        apiEndpoint: String
    ): Request.Builder

    protected abstract fun addPostBody(
        requestBuilder: Request.Builder,
        request: CodeRequest,
        stream: Boolean
    ): Request.Builder

    protected open suspend fun toOkHttpRequest(request: CodeRequest, stream: Boolean): Request {
        var requestBuilder = Request.Builder()
            .url(request.apiEndpoint)
            .header("Content-Type", "application/json")
            .addHeader("Date", getUTCDate())
        if (stream) {
            requestBuilder = requestBuilder.addHeader("Accept", "text/event-stream")
        }
        requestBuilder = addAuthorization(requestBuilder, request.apiEndpoint)
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
                (clientResponse?.body?.string()?.let {
                    kotlin.runCatching { toCodeResponse(it, stream) }.getOrNull()
                }?.error ?: ErrorImpl()).getShowError()
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