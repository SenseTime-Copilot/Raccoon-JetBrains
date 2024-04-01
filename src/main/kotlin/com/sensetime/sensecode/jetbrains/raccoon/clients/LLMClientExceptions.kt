package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import java.lang.Exception


private val LOG = logger<LLMClientException>()

internal sealed class LLMClientException(message: String, private val details: String?) : Exception(message) {
    constructor(
        type: String, message: String?, details: String?
    ) : this("${type}${message.ifNullOrBlankElse(" Error!") { ": $it" }}", details)

    init {
        LOG.warn(toString())
        LOG.debug(this) { "details: $details" }
    }
}

internal class LLMClientUnknownException : LLMClientException("Unknown", null, null)
internal class LLMClientMessageException(message: String, details: String? = null) :
    LLMClientException(message, details)

internal class LLMClientCanceledException(message: String? = null, details: String? = null) :
    LLMClientException("Canceled", message, details)

internal class LLMClientErrorCodeException(code: Int, message: String?, details: String? = null) :
    LLMClientException("ErrorCode $code", message, details)

internal class LLMClientUnauthorizedException(message: String? = null, details: String? = null) :
    LLMClientException("Unauthorized", message, details)

internal class LLMClientSensitiveException(message: String? = null, details: String? = null) :
    LLMClientException("Sensitive", message, details)

internal class LLMClientStreamException(message: String, details: String? = null) :
    LLMClientException("SSE(Streaming)", message, details)
