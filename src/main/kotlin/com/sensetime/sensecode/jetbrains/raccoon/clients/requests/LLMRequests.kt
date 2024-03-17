package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import kotlinx.serialization.Serializable


// LLM messages

sealed interface LLMMessage {
    enum class Role(val defaultName: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool")
    }

    val role: Role
}

sealed interface LLMContentMessage : LLMMessage {
    val content: String
}

sealed interface LLMAgentMessage : LLMMessage
sealed interface LLMChatMessage : LLMContentMessage, LLMAgentMessage

data class LLMSystemMessage(override val content: String) : LLMChatMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.SYSTEM
}

data class LLMUserMessage(override val content: String) : LLMChatMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.USER
}

abstract class LLMAssistantMessageBase : LLMMessage {
    final override val role: LLMMessage.Role = LLMMessage.Role.ASSISTANT
}

data class LLMAssistantMessage(override val content: String) : LLMAssistantMessageBase(), LLMChatMessage


@Serializable
data class LLMFunction(val name: String, val arguments: String)

@Serializable
data class LLMToolCall(
    val id: String,
    val function: LLMFunction
) {
    val type: String = "function"
}

data class LLMToolCallsMessage(
    val toolCalls: List<LLMToolCall>
) : LLMAssistantMessageBase(), LLMAgentMessage

data class LLMToolMessage(
    override val content: String,
    val toolCallID: String
) : LLMContentMessage, LLMAgentMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.TOOL
}


// LLM requests, same for all client

sealed interface LLMRequest

data class LLMCompletionRequest(
    val n: Int,
    val prompt: String
) : LLMRequest

data class LLMChatRequest(
    val id: String,
    val messages: List<LLMChatMessage>
) : LLMRequest

data class LLMAgentRequest(
    val id: String,
    val messages: List<LLMAgentMessage>
) : LLMRequest
