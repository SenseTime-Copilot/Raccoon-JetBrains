package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// LLM messages

internal sealed interface LLMMessage {
    enum class Role(val defaultName: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool")
    }

    val role: Role
}

internal sealed interface LLMContentMessage : LLMMessage {
    val content: String
}

internal sealed interface LLMAgentMessage : LLMMessage
internal sealed interface LLMChatMessage : LLMContentMessage, LLMAgentMessage

internal data class LLMSystemMessage(override val content: String) : LLMChatMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.SYSTEM
}

internal data class LLMUserMessage(override val content: String) : LLMChatMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.USER
}

internal abstract class LLMAssistantMessageBase : LLMMessage {
    final override val role: LLMMessage.Role = LLMMessage.Role.ASSISTANT
}

internal data class LLMAssistantMessage(override val content: String) : LLMAssistantMessageBase(), LLMChatMessage


@Serializable
internal data class LLMFunction(val name: String, val arguments: String)

@Serializable
data class PromptData(
    val language_id: String,
    val prefix: String,
    val suffix: String
)
@Serializable
internal data class LLMToolCall(
    val id: String,
    val function: LLMFunction
) {
    val type: String = "function"
}

@Serializable
data class FunctionCallInfo(
    val file_name: String,
    val file_chunk: String
)

@Serializable
data class LocalKnows(
    val local_knows: List<FunctionCallInfo>
)

internal data class LLMToolCallsMessage(
    val toolCalls: List<LLMToolCall>
) : LLMAssistantMessageBase(), LLMAgentMessage

internal data class LLMToolMessage(
    override val content: String,
    val toolCallID: String
) : LLMContentMessage, LLMAgentMessage {
    override val role: LLMMessage.Role = LLMMessage.Role.TOOL
}

@Serializable
internal data class LLMCodeChunk(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("file_chunk")
    val fileChunk: String
)


// LLM requests, same for all client

internal sealed class LLMRequest {
    abstract val n: Int
    protected abstract val stream: Boolean?
    fun isStream(): Boolean = stream ?: (n <= 1)
    abstract val action: String
    abstract val maxNewTokens: Int
}

internal data class LLMCompletionRequest(
    override val n: Int,
    override val stream: Boolean? = null,
    override val action: String = "inline completion",
    override val maxNewTokens: Int = -1,
    val prompt: PromptData,
    val knowledgeJSON: LocalKnows? = null
) : LLMRequest()

internal data class LLMChatRequest(
    val id: String,
    override val n: Int = 1,
    override val stream: Boolean? = true,
    override val maxNewTokens: Int = -1,
    override val action: String,
    val messages: List<LLMChatMessage>,
    val localKnowledge: List<LLMCodeChunk>? = null
) : LLMRequest()

internal data class LLMAgentRequest(
    val id: String,
    override val n: Int = 1,
    override val stream: Boolean? = true,
    override val maxNewTokens: Int = -1,
    override val action: String,
    val messages: List<LLMAgentMessage>
) : LLMRequest()
