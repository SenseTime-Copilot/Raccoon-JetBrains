package com.sensetime.sensecore.sensecodeplugin.openapi

import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import com.sensetime.sensecore.sensecodeplugin.openapi.response.ErrorResponse
import com.sensetime.sensecore.sensecodeplugin.openapi.response.streaming.ChatCompletion
import com.sensetime.sensecore.sensecodeplugin.security.GptMentorCredentialsManager
import io.ktor.client.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSources
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RealOpenApi(
    private val client: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val credentialsManager: GptMentorCredentialsManager,
) : OpenApi {

    private fun getUTCDate(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getAuthorization(date: String): String {
        return try {
            val message = "date: ${date}\nPOST /studio/ams/data/v1/chat/completions HTTP/1.1"

            val sha256HMAC = Mac.getInstance("HmacSHA256")
            sha256HMAC.init(SecretKeySpec(credentialsManager.getSecretKey().toByteArray(), "HmacSHA256"))
            val signature = Base64.getEncoder().encodeToString(sha256HMAC.doFinal(message.toByteArray()))

            val ak = credentialsManager.getAccessKey()
            """
                hmac accesskey="$ak", algorithm="hmac-sha256", headers="date request-line", signature="$signature"
            """.trimIndent()
        } catch (e: Exception) {
            ""
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun executeBasicActionStreaming(chatGptRequest: ChatGptRequest) =
        callbackFlow {
            val date = getUTCDate()
            val request = Request.Builder()
                .url(API_ENDPOINT)
                .header("Authorization", getAuthorization(date))
                .addHeader("Accept", "text/event-stream")
                .addHeader("Content-Type", "application/json")
                .addHeader("Date", date)
                .post(JSON.encodeToString(ChatGptRequest.serializer(), chatGptRequest).toRequestBody())
                .build()

            val listener = ChatGptEventSourceListener(
                logger = logger,
                onError = { response ->
                    try {
                        val errorResponse = response?.body?.string()?.let {
                            JSON.decodeFromString(ErrorResponse.serializer(), it)
                        }
                        val message = listOfNotNull(
                            response?.code?.let { "Http status: $it" },
                            "message: ${errorResponse?.message?.takeIf { it.isNotEmpty() } ?: response?.message?.takeIf { it.isNotEmpty() } ?: UNKNOWM_ERROR}",
                            errorResponse?.code?.let { "server code: $it" },
                            errorResponse?.details?.firstOrNull()?.reason?.let { "reason: $it" }
                        ).joinToString()
                        trySend(StreamingResponse.Error(message))
                    } catch (e: Exception) {
                        trySend(StreamingResponse.Error(e.message ?: UNKNOWM_ERROR))
                    }
                },
            ) { response ->
                try {
                    if (response == MESSAGE_DONE) {
                        trySend(StreamingResponse.Done)
                    } else {
                        val chatCompletion = JSON.decodeFromString(ChatCompletion.serializer(), response)
                        val content = chatCompletion.choices?.firstOrNull()?.message?.content ?: ""
                        trySend(StreamingResponse.Data(content))
                    }
                } catch (e: Exception) {
                    logger.error("Error while processing response", e)
                    if (e is CancellationException) {
                        throw e
                    } else {
                        trySend(StreamingResponse.Error(e.message ?: UNKNOWM_ERROR))
                    }
                }
            }

            val eventSource = EventSources.createFactory(okHttpClient)
                .newEventSource(request = request, listener = listener)

            awaitClose {
                eventSource.cancel()
            }
        }

    companion object {
        const val API_ENDPOINT = "https://ams.sensecoreapi.cn/studio/ams/data/v1/chat/completions"
        private const val UNKNOWM_ERROR = "Unknown error"
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RealOpenApi::class.java)
        private const val MESSAGE_DONE = "[DONE]"
    }
}
