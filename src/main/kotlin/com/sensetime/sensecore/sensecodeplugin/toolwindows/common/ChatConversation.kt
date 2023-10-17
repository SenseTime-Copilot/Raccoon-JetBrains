package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatConversation(
    val name: String,
    val user: Message,
    val assistant: Message? = null,
    val state: State = State.PROMPT
) {
    enum class State(val code: String) {
        PROMPT("prompt"),
        GENERATING("generating"),
        DONE("done"),
        STOPPED("stopped"),
        ERROR("error")
    }

    @Serializable
    data class Message(
        val timestampMs: Long,
        val args: Map<String, String>
    ) {
        companion object {
            const val RAW = "raw"
            const val CODE = "code"
            fun makeMessage(
                raw: String,
                code: String? = null,
                args: Map<String, String>? = null,
                timestampMs: Long = getCurrentTimestampMs()
            ): Message = Message(timestampMs, buildMap {
                args?.let { putAll(it) }
                put(RAW, raw)
                code?.let { put(CODE, it) }
            })
        }

        val raw: String?
            get() = args[RAW]
        val code: String?
            get() = args[CODE]
    }

    fun toPromptConversation(): ChatConversation = ChatConversation(name, user)

    companion object {
        fun getCurrentTimestampMs() = System.currentTimeMillis()
    }
}

fun List<ChatConversation>.toJsonString() =
    SenseCodeChatJson.encodeToString(ListSerializer(ChatConversation.serializer()), this)

fun String.toChatConversations(): List<ChatConversation> =
    SenseCodeChatJson.decodeFromString(ListSerializer(ChatConversation.serializer()), this)

fun List<ChatConversation>.toCodeRequestMessage(
    promptTemplate: ModelConfig.PromptTemplate
): List<CodeRequest.Message> = promptTemplate.run {
    listOfNotNull(getSystemPromptContent()?.let { CodeRequest.Message(systemRole, it) }) + flatMap { conversation ->
        when (conversation.state) {
            ChatConversation.State.PROMPT -> listOf(
                CodeRequest.Message(
                    userRole,
                    getUserPromptContent(conversation.user.args)
                )
            )

            ChatConversation.State.DONE -> listOf(
                CodeRequest.Message(userRole, getUserPromptContent(conversation.user.args)),
                CodeRequest.Message(assistantRole, getAssistantTextContent(conversation.assistant!!.args))
            )

            else -> emptyList()
        }
    }
}