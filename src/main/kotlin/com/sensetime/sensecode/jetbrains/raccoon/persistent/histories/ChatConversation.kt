package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMAssistantMessage
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatMessage
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMUserMessage
import com.sensetime.sensecode.jetbrains.raccoon.llm.tokens.RaccoonTokenUtils
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import kotlinx.serialization.Serializable


@Serializable
internal data class ChatConversation(
    val user: UserMessage,
    val assistant: AssistantMessage? = AssistantMessage.createPromptAssistantMessage(),
    val id: String? = null,
) {
    fun toPromptConversation(): ChatConversation = ChatConversation(user, id = id)
    fun toHistoryConversation(): ChatConversation = ChatConversation(user, null, id)
    fun toSensitiveConversation(errorMessage: String): ChatConversation = ChatConversation(
        UserMessage.createUserMessage(
            null,
            user.name,
            user.promptType,
            RaccoonBundle.message("toolwindow.content.chat.conversation.sensitive.user.message"),
            timestampMs = user.timestampMs
        )!!, AssistantMessage(errorMessage, AssistantMessage.GenerateState.ERROR), id
    )
}

internal fun List<ChatConversation>.getID(): String? = lastOrNull()?.id

internal fun List<ChatConversation>.toCodeRequestMessage(modelConfig: ChatModelConfig): List<LLMChatMessage> {
    var currentTokens = 0
    val maxInputTokens = modelConfig.maxInputTokens

    return listOfNotNull(modelConfig.getLLMSystemMessage()) + (reversed().flatMapIndexed { index, conversation ->
        if (index <= 0) {
            val content = conversation.user.getContent(modelConfig)
            currentTokens += RaccoonTokenUtils.estimateTokensNumber(content)
            listOf(LLMUserMessage(content))
        } else {
            conversation.assistant?.takeIf { (currentTokens >= 0) && (AssistantMessage.GenerateState.DONE == it.generateState) }
                ?.let { assistant ->
                    val userContent = conversation.user.getContent(modelConfig)
                    val tmpLength = RaccoonTokenUtils.estimateTokensNumber(userContent + assistant.content)
                    if (currentTokens + tmpLength <= maxInputTokens) {
                        currentTokens += tmpLength
                        listOf(
                            LLMAssistantMessage(assistant.content), LLMUserMessage(userContent)
                        )
                    } else {
                        currentTokens = -1
                        null
                    }
                } ?: emptyList()
        }
    }).reversed()
}
