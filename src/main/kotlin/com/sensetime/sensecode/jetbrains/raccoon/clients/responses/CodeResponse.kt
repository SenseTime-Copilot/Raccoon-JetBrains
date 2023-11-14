package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Common {
    val id: String?
    val model: String?
    val `object`: String?
    val created: Int?
}

interface Choice {
    val index: Int?
    val token: String?
    val finishReason: String?

    fun hasToken(): Boolean = !token.isNullOrBlank()
    fun hasFinishReason(): Boolean = !finishReason.isNullOrBlank()
}

interface Usage {
    val prompt: Int?
    val completion: Int?

    fun getTotal(): Int? = prompt?.let { a -> completion?.let { b -> a + b } }
    fun hasUsage(): Boolean {
        val total = getTotal()
        return (null != total) && total > 0
    }

    fun getShowString(): String? =
        if (hasUsage()) "Usage: prompt($prompt) + completion($completion) = ${getTotal()}" else null
}

@Serializable
class UsageImpl(
    @SerialName("prompt_tokens")
    override val prompt: Int? = null,
    @SerialName("completion_tokens")
    override val completion: Int? = null
) : Usage

interface Error {
    val error: String?

    fun hasError(): Boolean = !error.isNullOrBlank()
    fun getShowError(): String = error.ifNullOrBlank(UNKNOWN_ERROR)

    companion object {
        const val UNKNOWN_ERROR: String = "Unknown error"
    }
}

interface CodeResponse : Common {
    val usage: Usage?
    val choices: List<Choice>?
    val error: Error?

    fun toStreamResponse(): List<CodeStreamResponse> {
        val result = listOfNotNull(choices?.let { CodeStreamResponse.TokenChoices(this, it) },
            usage?.takeIf { it.hasUsage() }?.let { CodeStreamResponse.TokenUsage(this, it) },
            error?.takeIf { it.hasError() }?.let { CodeStreamResponse.Error(it.getShowError()) })
        return result.ifEmpty { listOf(CodeStreamResponse.Error(Error.UNKNOWN_ERROR)) }
    }
}

sealed class CodeStreamResponse {
    data object Connected : CodeStreamResponse()
    data class TokenChoices(val common: Common, val choices: List<Choice>) : CodeStreamResponse()
    data class TokenUsage(val common: Common, val usage: Usage) : CodeStreamResponse()
    data class Error(val error: String) : CodeStreamResponse()
    data object Done : CodeStreamResponse()
    data object Closed : CodeStreamResponse()
}