package com.sensetime.intellij.plugins.sensecode.persistent.histories

import com.sensetime.intellij.plugins.sensecode.clients.requests.CodeRequest
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import kotlinx.serialization.Serializable

@Serializable
data class ChatConversation(
    val user: UserMessage,
    val assistant: AssistantMessage? = AssistantMessage.createPromptAssistantMessage(),
) {
    fun toPromptConversation(): ChatConversation = ChatConversation(user)
    fun toHistoryConversation(): ChatConversation = ChatConversation(user, null)
}

fun List<ChatConversation>.toCodeRequestMessage(modelConfig: ModelConfig): List<CodeRequest.Message> {
    val userRole = modelConfig.getRoleString(ModelConfig.Role.USER)
    val assistantRole = modelConfig.getRoleString(ModelConfig.Role.ASSISTANT)
    return listOfNotNull(
        modelConfig.getSystemPromptPair()
            ?.let { CodeRequest.Message(it.first, it.second) }) + flatMapIndexed { index, conversation ->
        if (index >= lastIndex) {
            listOf(CodeRequest.Message(userRole, conversation.user.getContent(modelConfig)))
        } else {
            conversation.assistant?.takeIf { AssistantMessage.GenerateState.DONE == it.generateState }
                ?.let { assistant ->
                    listOf(
                        CodeRequest.Message(userRole, conversation.user.getContent(modelConfig)),
                        CodeRequest.Message(assistantRole, assistant.content)
                    )
                } ?: emptyList()
        }
    }
}
