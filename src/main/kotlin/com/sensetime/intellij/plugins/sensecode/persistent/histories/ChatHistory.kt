package com.sensetime.intellij.plugins.sensecode.persistent.histories

import com.sensetime.intellij.plugins.sensecode.persistent.SenseCodePersistentJson
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ChatHistory(
    var userPromptText: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val timestampMs: Long = SenseCodeUtils.getCurrentTimestampMs()
) {
    fun hasData(): Boolean = (true == conversations.firstOrNull()?.user?.hasData())
}

fun ChatHistory.toJsonString(): String =
    SenseCodePersistentJson.encodeToString(ChatHistory.serializer(), this)

fun String.toChatHistory(): ChatHistory =
    SenseCodePersistentJson.decodeFromString(ChatHistory.serializer(), this)

fun List<ChatHistory>.toJsonString(): String =
    SenseCodePersistentJson.encodeToString(ListSerializer(ChatHistory.serializer()), filter { it.hasData() })

fun String.toChatHistories(): List<ChatHistory> =
    SenseCodePersistentJson.decodeFromString(ListSerializer(ChatHistory.serializer()), this).filter { it.hasData() }

