package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientJson
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientJsonObject
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient.Companion.getIsKnowledgeBaseAllowed
import com.sensetime.sensecode.jetbrains.raccoon.clients.toJsonArray
import com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.AgentModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.plusIfNotNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*


internal abstract class NovaMessage(
    role: LLMMessage.Role, modelConfig: ModelConfig, messageArgs: Map<String, JsonElement>
) : LLMClientJsonObject(mapOf("role" to JsonPrimitive(modelConfig.getRoleString(role))) + messageArgs)

internal abstract class NovaContentMessage(
    role: LLMMessage.Role, modelConfig: ModelConfig, content: String, messageArgs: Map<String, JsonElement>? = null
) : NovaMessage(role, modelConfig, mapOf("content" to JsonPrimitive(content)).plusIfNotNull(messageArgs))

internal class NovaChatMessage(
    llmChatMessage: LLMChatMessage, modelConfig: ModelConfig
) : NovaContentMessage(llmChatMessage.role, modelConfig, llmChatMessage.content)

internal class NovaToolCallsMessage(
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

internal class NovaToolMessage(
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
@OptIn(ExperimentalSerializationApi::class)
internal data class NovaClientCommonParameters(
//    val model: String,
//    val temperature: Float,
    val n: Int,
    val stream: Boolean,
    val stop: String,
    @SerialName("max_new_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxTokens: Int? = null
) {
    constructor(modelConfig: ModelConfig, llmRequest: LLMRequest) : this(
//        modelConfig.name,
//        modelConfig.temperature,
        llmRequest.n, llmRequest.isStream(),
        modelConfig.stop.first(),
        llmRequest.maxNewTokens.takeIf { it > 0 } ?: modelConfig.getMaxNewTokens()
    )
}

internal abstract class NovaClientRequest(
    requestArgs: Map<String, JsonElement>, modelConfig: ModelConfig, llmRequest: LLMRequest
) : ClientRequest(
    LLMClientJson.encodeToJsonElement(
        NovaClientCommonParameters.serializer(),
        NovaClientCommonParameters(modelConfig, llmRequest)
    ).jsonObject + requestArgs, modelConfig.customRequestArgs
)

internal class NovaClientCompletionRequest(
    completionRequest: LLMCompletionRequest,
    modelConfig: CompletionModelConfig
) : NovaClientRequest(
    buildMap {
        val promptJson = Json.encodeToJsonElement(completionRequest.prompt)
        put("input", promptJson)
        put("local_knows", Json.encodeToJsonElement(completionRequest.knowledgeJSON?.local_knows))
        RaccoonUserInformation.getInstance().knowledgeBases?.knowledgeBases?.takeIf { getIsKnowledgeBaseAllowed() && RaccoonSettingsState.instance.isCloudKnowledgeBaseEnabled }
            ?.let { knowledgeBases ->
                put("know_ids", JsonArray(knowledgeBases.map { JsonPrimitive(it.code) }))
            }

//        put("input", JsonPrimitive(completionRequest.prompt))
        if (RaccoonSettingsState.instance.inlineCompletionPreference == CompletionModelConfig.CompletionPreference.SPEED_PRIORITY) {
            put("stop", JsonPrimitive("\n"))
        }
    }, modelConfig, completionRequest
)

internal class NovaClientChatRequest(
    chatRequest: LLMChatRequest,
    modelConfig: ChatModelConfig,
    customRequestArgs: Map<String, JsonElement>? = null
) : NovaClientRequest(
    buildMap {
//        put("messages", chatRequest.messages.toNovaChatMessages(modelConfig).toJsonArray())
        val additionalMessage = NovaChatMessage(
            LLMSystemMessage(RaccoonBundle.message("agent.default")),
            modelConfig
        )

        // Add the new message before the existing messages
        val allMessages = listOf(additionalMessage) + chatRequest.messages.toNovaChatMessages(modelConfig)

        put("messages", allMessages.toJsonArray())
//
        print(chatRequest.messages);
        chatRequest.localKnowledge?.let {
            put(
                "local_knows",
                LLMClientJson.encodeToJsonElement(ListSerializer(LLMCodeChunk.serializer()), it)
            )
        }
        customRequestArgs?.let { putAll(it) }
    }, modelConfig, chatRequest
)

internal class NovaClientAgentRequest(
    agentRequest: LLMAgentRequest,
    modelConfig: AgentModelConfig
) : NovaClientRequest(
    mapOf(
        "tools" to modelConfig.tools,
        "messages" to agentRequest.messages.toNovaAgentMessage(modelConfig).toJsonArray()
    ), modelConfig, agentRequest
)
