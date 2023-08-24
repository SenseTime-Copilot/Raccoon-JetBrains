package com.sensetime.sensecore.sensecodeplugin.openapi.response.streaming

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRespError(
    val error: ErrorMessage
)

@Serializable
data class ErrorMessage(
    val message: String
)

@Serializable
data class ChatCompletion(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    val message: Message,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
)

@Serializable
data class Message(
    val role: String? = null,
    val content: String? = null,
)
