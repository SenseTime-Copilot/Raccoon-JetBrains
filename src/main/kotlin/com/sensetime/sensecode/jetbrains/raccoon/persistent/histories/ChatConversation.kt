package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import kotlinx.serialization.Serializable

@Serializable
data class ChatConversation(
    val user: UserMessage,
    val assistant: AssistantMessage? = AssistantMessage.createPromptAssistantMessage(),
    val id: String? = null,
) {
    fun toPromptConversation(): ChatConversation = ChatConversation(user, id = id)
    fun toHistoryConversation(): ChatConversation = ChatConversation(user, null, id)
    fun toSensitiveConversation(errorMessage: String): ChatConversation = ChatConversation(
        UserMessage.createUserMessage(
            user.name,
            user.promptType,
            RaccoonBundle.message("toolwindow.content.chat.conversation.sensitive.user.message"),
            timestampMs = user.timestampMs
        )!!, AssistantMessage(errorMessage, AssistantMessage.GenerateState.ERROR), id
    )
}

fun List<ChatConversation>.getID(): String? = lastOrNull()?.id

fun List<ChatConversation>.toCodeRequestMessage(modelConfig: ModelConfig): List<CodeRequest.Message> {
    val userRole = modelConfig.getRoleString(ModelConfig.Role.USER)
    val assistantRole = modelConfig.getRoleString(ModelConfig.Role.ASSISTANT)
    val maxInputTokens = modelConfig.maxInputTokens
    var currentTokens = 0
    return listOfNotNull(
        modelConfig.getSystemPromptPair()
            ?.let { CodeRequest.Message(it.first, it.second) }) + ((reversed().flatMapIndexed { index, conversation ->
        if (index <= 0) {
            val content = conversation.user.getContent(modelConfig)
            currentTokens += content.length
            listOf(CodeRequest.Message(userRole, content))
        } else {
            conversation.assistant?.takeIf { (currentTokens >= 0) && (AssistantMessage.GenerateState.DONE == it.generateState) }
                ?.let { assistant ->
                    val userContent = conversation.user.getContent(modelConfig)
                    val assistantContent = assistant.content
                    val tmpLength = userContent.length + assistantContent.length
                    if (currentTokens + tmpLength <= maxInputTokens) {
                        currentTokens += tmpLength
                        listOf(
                            CodeRequest.Message(assistantRole, assistantContent),
                            CodeRequest.Message(userRole, userContent)
                        )
                    } else {
                        currentTokens = -1
                        null
                    }
                } ?: emptyList()
        }
    }).reversed())
}
