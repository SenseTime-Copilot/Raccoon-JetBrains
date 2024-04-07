package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import okhttp3.Response
import java.lang.Exception


private val LOG = logger<LLMClientException>()

internal sealed class LLMClientException(message: String, val details: String?) : Exception(message) {
    constructor(
        type: String, message: String?, details: String?
    ) : this("${type}${message.ifNullOrBlankElse(" Error!") { ": $it" }}", details)

    init {
        LOG.warn(toString())
        LOG.debug(this) { "details: $details" }
    }
}

internal class LLMClientUnknownException : LLMClientException("Unknown", null, null)
internal class LLMClientMessageException(val rawMessage: String, details: String? = null) :
    LLMClientException(rawMessage, details)

internal class LLMClientErrorCodeException(code: Int, message: String?, details: String? = null) :
    LLMClientException("ErrorCode $code", message, details)

internal class LLMClientUnauthorizedException(
    message: String? = null, details: String? = null
) : LLMClientException("Unauthorized", message, details) {
    constructor(llmClientMessageException: LLMClientMessageException) : this(
        llmClientMessageException.rawMessage, llmClientMessageException.details
    )
}

internal class LLMClientSensitiveException(message: String? = null, details: String? = null) :
    LLMClientException("Sensitive", message, details)

internal class LLMClientStreamException(message: String, details: String? = null) :
    LLMClientException("SSE(Streaming)", message, details)

internal fun Response.isHttpServerError(): Boolean = (code in 500..599)

internal class LLMClientResponseException(
    val response: Response, message: String, details: String? = null
) : LLMClientException(message, details) {
    constructor(llmClientMessageException: LLMClientMessageException, response: Response) : this(
        response, llmClientMessageException.rawMessage, llmClientMessageException.details
    )

    fun isHttpServerError(): Boolean = response.isHttpServerError()
}