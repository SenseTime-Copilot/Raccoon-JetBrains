package com.sensetime.sensecore.sensecodeplugin.toolwindows.history

import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.SenseCodeChatJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatHistory(
    val chatType: Type,
    val conversations: List<ChatConversation>,
    val timestampMs: Long = ChatConversation.getCurrentTimestampMs()
) {
    enum class Type(val code: String) {
        FREE_CHAT("freeChat"),
        CODE_TASK("codeTask")
    }
}

fun List<ChatHistory>.toJsonString() =
    SenseCodeChatJson.encodeToString(ListSerializer(ChatHistory.serializer()), this)

fun String.toChatHistories(): List<ChatHistory> =
    SenseCodeChatJson.decodeFromString(ListSerializer(ChatHistory.serializer()), this)