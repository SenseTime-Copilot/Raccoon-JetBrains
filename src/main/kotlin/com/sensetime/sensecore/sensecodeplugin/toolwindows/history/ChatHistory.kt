package com.sensetime.sensecore.sensecodeplugin.toolwindows.history

import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.SenseCodeChatJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatHistory(
    val chatType: ChatType,
    var userPromptText: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val timestampMs: Long = ChatConversation.getCurrentTimestampMs()
) {
    enum class ChatType(val code: String) {
        FREE_CHAT(ClientConfig.FREE_CHAT),
        CODE_TASK("codeTask")
    }

    fun hasData(): Boolean = (null != conversations.firstOrNull()?.user?.raw?.takeIf { it.isNotBlank() })
}

fun ChatHistory.toJsonString(): String =
    SenseCodeChatJson.encodeToString(ChatHistory.serializer(), this)

fun String.toChatHistory(): ChatHistory =
    SenseCodeChatJson.decodeFromString(ChatHistory.serializer(), this)

fun List<ChatHistory>.toJsonString(): String =
    SenseCodeChatJson.encodeToString(ListSerializer(ChatHistory.serializer()), this)

fun String.toChatHistories(): List<ChatHistory> =
    SenseCodeChatJson.decodeFromString(ListSerializer(ChatHistory.serializer()), this)