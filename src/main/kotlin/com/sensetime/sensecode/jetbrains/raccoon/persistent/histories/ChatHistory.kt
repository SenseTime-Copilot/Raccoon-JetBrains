package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonPersistentJson
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer


@Serializable
internal data class ChatHistory(
    var userPromptText: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val timestampMs: Long = RaccoonUtils.getDateTimestampMs()
) {
    fun hasData(): Boolean = (true == conversations.firstOrNull()?.user?.hasData())
}

internal fun ChatHistory.toJsonString(): String =
    RaccoonPersistentJson.encodeToString(ChatHistory.serializer(), this)

internal fun String.toChatHistory(): ChatHistory =
    RaccoonPersistentJson.decodeFromString(ChatHistory.serializer(), this)

internal fun List<ChatHistory>.toJsonString(): String =
    RaccoonPersistentJson.encodeToString(ListSerializer(ChatHistory.serializer()), this)

internal fun String.toChatHistories(): List<ChatHistory> =
    RaccoonPersistentJson.decodeFromString(ListSerializer(ChatHistory.serializer()), this)
