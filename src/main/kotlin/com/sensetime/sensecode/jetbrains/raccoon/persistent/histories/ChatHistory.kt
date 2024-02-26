package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonPersistentJson
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatHistory(
    var userPromptText: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val timestampMs: Long = RaccoonUtils.getSystemTimestampMs()
) {
    fun hasData(): Boolean = (true == conversations.firstOrNull()?.user?.hasData())
}

fun ChatHistory.toJsonString(): String =
    RaccoonPersistentJson.encodeToString(ChatHistory.serializer(), this)

fun String.toChatHistory(): ChatHistory =
    RaccoonPersistentJson.decodeFromString(ChatHistory.serializer(), this)

fun List<ChatHistory>.toJsonString(): String =
    RaccoonPersistentJson.encodeToString(ListSerializer(ChatHistory.serializer()), this)

fun String.toChatHistories(): List<ChatHistory> =
    RaccoonPersistentJson.decodeFromString(ListSerializer(ChatHistory.serializer()), this)
