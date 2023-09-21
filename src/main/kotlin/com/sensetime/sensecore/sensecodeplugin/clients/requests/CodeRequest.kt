package com.sensetime.sensecore.sensecodeplugin.clients.requests

import kotlinx.serialization.Serializable

@Serializable
data class CodeRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float,
    val n: Int,
    val stop: String,
    val maxTokens: Int,
    val apiEndpoint: String
) {
    @Serializable
    data class Message(val role: String, val content: String)
}
