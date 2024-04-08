package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientErrorCodeException
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientMessageException
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientSensitiveException
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMToolCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal abstract class ClientCodeStatus(
    private val code: Int = OK_CODE,
    protected val message: String? = null
) : LLMResponseError {
    protected abstract fun getDetailsInfo(): String?
    protected fun takeIfCodeNotOk(): Int? = code.takeIf { it != OK_CODE }

    override fun throwIfError() {
        takeIfCodeNotOk()?.let { throw LLMClientErrorCodeException(it, message, getDetailsInfo()) }
    }

    companion object {
        const val OK_CODE = 0
    }
}

@Serializable
internal data class NovaClientStatus(
    private val details: List<String>? = null
) : ClientCodeStatus() {
    override fun getDetailsInfo(): String? = details?.toString()

    override fun throwIfError() {
        takeIfCodeNotOk()?.let { c ->
            when (c) {
                SENSITIVE_CODE -> throw LLMClientSensitiveException(message, getDetailsInfo())
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
internal data class NovaClientLLMUsage(override val prompt: Int = 0, override val completion: Int = 0) : LLMUsage

@Serializable
internal sealed class NovaClientLLMChoice(
    override val index: Int = -1,
    @SerialName("finish_reason")
    override val finishReason: String? = null
) : LLMChoice

@Serializable
internal open class NovaClientLLMContentChoice(
    private val text: String? = null,
    private val message: String? = null,
    private val delta: String? = null,
) : NovaClientLLMChoice(), LLMContentChoice {
    override val token: String?
        get() = message ?: text ?: delta
}

@Serializable
internal class NovaClientLLMCompletionChoice : NovaClientLLMContentChoice(), LLMCompletionChoice

@Serializable
internal class NovaClientLLMChatChoice : NovaClientLLMContentChoice(), LLMChatChoice

@Serializable
internal data class NovaClientLLMAgentChoice(
    override val type: String? = null,
    @SerialName("tool_calls")
    override val toolCalls: List<LLMToolCall>? = null
) : NovaClientLLMContentChoice(), LLMAgentChoice

@Serializable
internal data class NovaClientLLMResponseData<T : NovaClientLLMChoice>(
    val status: NovaClientStatus? = null,
    override val id: String? = null,
    override val usage: NovaClientLLMUsage? = null,
    override val choices: List<T>? = null
) : LLMResponseData<T>

internal typealias NovaClientLLMCompletionResponseData = NovaClientLLMResponseData<NovaClientLLMCompletionChoice>
internal typealias NovaClientLLMChatResponseData = NovaClientLLMResponseData<NovaClientLLMChatChoice>
internal typealias NovaClientLLMAgentResponseData = NovaClientLLMResponseData<NovaClientLLMAgentChoice>
