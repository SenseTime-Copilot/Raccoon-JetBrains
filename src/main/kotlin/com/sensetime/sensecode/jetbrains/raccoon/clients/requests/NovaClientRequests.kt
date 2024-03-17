package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientJson
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientJsonObject
import com.sensetime.sensecode.jetbrains.raccoon.clients.toJsonArray
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.AgentModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.utils.plusIfNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*


abstract class NovaMessage(
    role: LLMMessage.Role, modelConfig: ModelConfig, messageArgs: Map<String, JsonElement>
) : LLMClientJsonObject(mapOf("role" to JsonPrimitive(modelConfig.getRoleString(role))) + messageArgs)

abstract class NovaContentMessage(
    role: LLMMessage.Role, modelConfig: ModelConfig, content: String, messageArgs: Map<String, JsonElement>? = null
) : NovaMessage(role, modelConfig, mapOf("content" to JsonPrimitive(content)).plusIfNotNull(messageArgs))

class NovaChatMessage(
    llmChatMessage: LLMChatMessage, modelConfig: ModelConfig
) : NovaContentMessage(llmChatMessage.role, modelConfig, llmChatMessage.content)

class NovaToolCallsMessage(
    llmToolCallsMessage: LLMToolCallsMessage, modelConfig: ModelConfig
) : NovaMessage(
    llmToolCallsMessage.role, modelConfig,
    mapOf(
        "tool_calls" to LLMClientJson.encodeToJsonElement(
            ListSerializer(LLMToolCall.serializer()),
            llmToolCallsMessage.toolCalls
        )
    )
)

class NovaToolMessage(
    llmToolMessage: LLMToolMessage, modelConfig: ModelConfig
) : NovaContentMessage(
    llmToolMessage.role, modelConfig, llmToolMessage.content,
    mapOf("tool_call_id" to JsonPrimitive(llmToolMessage.toolCallID))
)

private fun List<LLMChatMessage>.toNovaChatMessages(modelConfig: ModelConfig): List<NovaChatMessage> =
    map { NovaChatMessage(it, modelConfig) }

private fun List<LLMAgentMessage>.toNovaAgentMessage(modelConfig: ModelConfig): List<NovaMessage> = map {
    when (it) {
        is LLMChatMessage -> NovaChatMessage(it, modelConfig)
        is LLMToolCallsMessage -> NovaToolCallsMessage(it, modelConfig)
        is LLMToolMessage -> NovaToolMessage(it, modelConfig)
    }
}


@Serializable
data class NovaClientCommonParameters(
    val model: String,
    val temperature: Float,
    val n: Int,
    val stream: Boolean,
    val stop: String,
    @SerialName("max_new_tokens")
    val maxTokens: Int
) {
    constructor(modelConfig: ModelConfig, n: Int, stream: Boolean?) : this(
        modelConfig.name,
        modelConfig.temperature,
        n, stream ?: (n <= 1),
        modelConfig.stop.first(),
        modelConfig.getMaxNewTokens()
    )
}

abstract class NovaClientRequest(
    requestArgs: Map<String, JsonElement>, modelConfig: ModelConfig, n: Int = 1, stream: Boolean? = null
) : ClientRequest(
    LLMClientJson.encodeToJsonElement(
        NovaClientCommonParameters.serializer(),
        NovaClientCommonParameters(modelConfig, n, stream)
    ).jsonObject + requestArgs, modelConfig.customRequestArgs
)

class NovaClientCompletionRequest(
    completionRequest: LLMCompletionRequest,
    modelConfig: CompletionModelConfig,
    stream: Boolean? = null
) : NovaClientRequest(
    mapOf("prompt" to JsonPrimitive(completionRequest.prompt)),
    modelConfig, completionRequest.n, stream
)

class NovaClientChatRequest(
    chatRequest: LLMChatRequest,
    modelConfig: ChatModelConfig
) : NovaClientRequest(
    mapOf("messages" to chatRequest.messages.toNovaChatMessages(modelConfig).toJsonArray()),
    modelConfig
)

class NovaClientAgentRequest(
    agentRequest: LLMAgentRequest,
    modelConfig: AgentModelConfig
) : NovaClientRequest(
    mapOf(
        "tools" to modelConfig.tools,
        "messages" to agentRequest.messages.toNovaAgentMessage(modelConfig).toJsonArray()
    ), modelConfig
)
