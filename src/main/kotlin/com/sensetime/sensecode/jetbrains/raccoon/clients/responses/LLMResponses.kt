package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMToolCall


interface LLMUsage {
    val prompt: Int
    val completion: Int

    fun getTotal(): Int = prompt + completion
    fun hasUsage(): Boolean = getTotal() > 0

    fun getDisplayUsage(): String = "Usage: prompt($prompt) + completion($completion) = ${getTotal()} tokens"
}

sealed interface LLMChoice {
    val index: Int
    val finishReason: String?
    fun hasFinishReason(): Boolean = !finishReason.isNullOrBlank()
}

interface LLMContentChoice : LLMChoice {
    val token: String?
    fun hasToken(): Boolean = !token.isNullOrBlank()
}

interface LLMAgentChoice : LLMContentChoice {
    val type: String?
    val toolCalls: List<LLMToolCall>?
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()
}

interface LLMResponseCommon {
    val id: String?
}

interface LLMResponseUsage {
    val usage: LLMUsage?
}

interface LLMResponseChoices {
    val choices: List<LLMChoice>?
    fun hasChoices(): Boolean = !choices.isNullOrEmpty()
}


interface LLMResponseData : LLMResponseCommon, LLMResponseUsage, LLMResponseChoices
interface LLMResponseError {
    fun throwIfError()
}

interface LLMResponse : LLMResponseError, LLMResponseData
