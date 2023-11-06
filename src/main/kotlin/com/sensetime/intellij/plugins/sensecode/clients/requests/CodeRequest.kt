package com.sensetime.intellij.plugins.sensecode.clients.requests

import kotlinx.serialization.Serializable

data class CodeRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float,
    val n: Int,
    val stop: String,
    val maxTokens: Int,
    val apiPath: String
) {
    @Serializable
    data class Message(val role: String, val content: String)
}