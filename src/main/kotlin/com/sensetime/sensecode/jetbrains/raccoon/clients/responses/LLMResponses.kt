package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMToolCall


internal interface LLMUsage {
    val prompt: Int
    val completion: Int

    fun getTotal(): Int = prompt + completion
    fun hasUsage(): Boolean = getTotal() > 0

    fun getDisplayUsage(): String = "Usage: prompt($prompt) + completion($completion) = ${getTotal()} tokens"
}

internal sealed interface LLMChoice {
    val index: Int
    val finishReason: String?
    fun hasFinishReason(): Boolean = !finishReason.isNullOrBlank()
}

internal interface LLMContentChoice : LLMChoice {
    val token: String?
    fun hasToken(): Boolean = !token.isNullOrBlank()
}

internal interface LLMCompletionChoice : LLMContentChoice
internal interface LLMChatChoice : LLMContentChoice

internal interface LLMAgentChoice : LLMContentChoice {
    val type: String?
    val toolCalls: List<LLMToolCall>?
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()
}

internal interface LLMResponseCommon {
    val id: String?
}

internal interface LLMResponseUsage {
    val usage: LLMUsage?
}

internal interface LLMResponseChoices<out T : LLMChoice> {
    val choices: List<T>?
    fun hasChoices(): Boolean = !choices.isNullOrEmpty()
}


internal interface LLMResponseData<out T : LLMChoice> : LLMResponseCommon, LLMResponseUsage, LLMResponseChoices<T>
internal interface LLMResponseError {
    fun throwIfError()
}

internal interface LLMResponse<out T : LLMChoice> : LLMResponseError, LLMResponseData<T>
internal typealias LLMCompletionResponse = LLMResponse<LLMCompletionChoice>
internal typealias LLMChatResponse = LLMResponse<LLMChatChoice>
internal typealias LLMAgentResponse = LLMResponse<LLMAgentChoice>
