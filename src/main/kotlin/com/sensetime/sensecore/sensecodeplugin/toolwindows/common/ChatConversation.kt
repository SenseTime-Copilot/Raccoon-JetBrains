package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
        val args:Map<String, String>,
        val timestampMs: Long = getCurrentTimestampMs()
    ) {
        companion object {
            const val RAW = "raw"
            const val CODE = "code"
            fun makeMessage(raw:String, code:String? = null, args:Map<String, String>?=null):Message = Message()
        }
        val raw:String?
            get() = args[RAW]
        val code:String?
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
    userRole: String,
    assistantRole: String,
    systemRole: String = "system",
    systemPrompt: String? = null
): List<CodeRequest.Message> =
    listOfNotNull(systemPrompt?.let { CodeRequest.Message(systemRole, it) }) + flatMap { conversation ->
        when (conversation.state) {
            ChatConversation.State.PROMPT -> listOf(CodeRequest.Message(userRole, conversation.user.content))
            ChatConversation.State.DONE -> listOf(
                CodeRequest.Message(userRole, conversation.user.content),
                CodeRequest.Message(assistantRole, conversation.assistant?.content ?: "")
            )

            else -> emptyList()
        }
    }