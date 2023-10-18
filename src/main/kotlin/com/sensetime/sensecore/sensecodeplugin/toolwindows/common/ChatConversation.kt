package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatConversation(
    val name: String,
    val type: String,
    val user: Message,
    val assistant: Message? = Message.makeMessage(""),
    var state: State = State.PROMPT
) {
    enum class State(val code: String) {
        PROMPT("prompt"),
        HISTORY("history"),
        DONE("done"),
        STOPPED("stopped"),
        ERROR("error")
    }

    @Serializable
    data class Message(
        val timestampMs: Long,
        val args: MutableMap<String, String>
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
            }.toMutableMap())
        }

        var raw: String
            get() = args.getOrDefault(RAW, "")
            set(value) {
                args[RAW] = value
            }
        val code: String?
            get() = args[CODE]

        fun hasData(): Boolean = !(raw.isBlank() && code.isNullOrBlank())
    }

    fun toPromptConversation(): ChatConversation = ChatConversation(name, type, user)
    fun toHistoryConversation(): ChatConversation = ChatConversation(name, type, user, null, State.HISTORY)

    companion object {
        fun getCurrentTimestampMs() = System.currentTimeMillis()
    }
}

fun List<ChatConversation>.toJsonString() =
    SenseCodeChatJson.encodeToString(ListSerializer(ChatConversation.serializer()), this)

fun String.toChatConversations(): List<ChatConversation> =
    SenseCodeChatJson.decodeFromString(ListSerializer(ChatConversation.serializer()), this)

fun List<ChatConversation>.toCodeRequestMessage(): List<CodeRequest.Message> {
    var firstPrompt: ModelConfig.PromptTemplate? = null
    val messages = flatMap { conversation ->
        val prompt = CodeClientManager.getClientAndConfigPair().second.getModelConfigByType(conversation.type)
            .getPromptTemplateByType(conversation.type)
        if (null == firstPrompt) {
            firstPrompt = prompt
        }
        when (conversation.state) {
            ChatConversation.State.PROMPT -> listOf(
                CodeRequest.Message(
                    prompt.userRole,
                    prompt.getUserPromptContent(conversation.user.args)
                )
            )

            ChatConversation.State.DONE -> listOf(
                CodeRequest.Message(prompt.userRole, prompt.getUserPromptContent(conversation.user.args)),
                CodeRequest.Message(prompt.assistantRole, prompt.getAssistantTextContent(conversation.assistant!!.args))
            )

            else -> emptyList()
        }
    }
    if (messages.isEmpty()) {
        return messages
    }
    return listOfNotNull(firstPrompt?.let { prompt ->
        prompt.getSystemPromptContent()?.let { CodeRequest.Message(prompt.systemRole, it) }
    }) + messages
}