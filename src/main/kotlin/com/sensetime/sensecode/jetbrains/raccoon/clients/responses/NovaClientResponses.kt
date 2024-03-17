package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientErrorCodeException
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientMessageException
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientSensitiveException
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMToolCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
abstract class ClientCodeStatus(
    private val code: Int = OK_CODE,
    private val message: String? = null
) : LLMResponseError {
    protected fun takeIfCodeNotOk(): Int? = code.takeIf { it != OK_CODE }
    protected fun takeIfMessageNotBlankOrOk(): String? =
        message?.takeIf { it.isNotBlank() && ("ok" != it) && ("success" != it) }

    protected abstract fun getDetailsInfo(): String?

    override fun throwIfError() {
        takeIfCodeNotOk()?.let { throw LLMClientErrorCodeException(it, message, getDetailsInfo()) }
        takeIfMessageNotBlankOrOk()?.let { throw LLMClientMessageException(it, getDetailsInfo()) }
    }

    companion object {
        const val OK_CODE = 0
    }
}

@Serializable
data class NovaClientStatus(
    private val details: List<String>? = null
) : ClientCodeStatus() {
    override fun getDetailsInfo(): String? = details?.toString()

    override fun throwIfError() {
        takeIfCodeNotOk()?.let { c ->
            when (c) {
                SENSITIVE_CODE -> throw LLMClientSensitiveException(takeIfMessageNotBlankOrOk(), getDetailsInfo())
                else -> Unit
            }
        }
        super.throwIfError()
    }

    companion object {
        private const val SENSITIVE_CODE = 18
    }
}

@Serializable
data class NovaClientLLMUsage(override val prompt: Int = 0, override val completion: Int = 0) : LLMUsage

@Serializable
sealed class NovaClientLLMChoice(
    override val index: Int = -1,
    @SerialName("finish_reason")
    override val finishReason: String? = null
) : LLMChoice

@Serializable
open class NovaClientLLMContentChoice(
    private val text: String? = null,
    private val message: String? = null,
    private val delta: String? = null,
) : NovaClientLLMChoice(), LLMContentChoice {
    override val token: String?
        get() = message ?: text ?: delta
}

@Serializable
data class NovaClientLLMAgentChoice(
    override val type: String? = null,
    @SerialName("tool_calls")
    override val toolCalls: List<LLMToolCall>? = null
) : NovaClientLLMContentChoice(), LLMAgentChoice

@Serializable
data class NovaClientLLMResponseData(
    val status: NovaClientStatus? = null,
    override val id: String? = null,
    override val usage: NovaClientLLMUsage? = null,
    override val choices: List<NovaClientLLMAgentChoice>? = null
) : LLMResponseData
