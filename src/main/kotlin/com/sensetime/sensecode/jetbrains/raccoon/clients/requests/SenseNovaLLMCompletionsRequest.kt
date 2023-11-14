package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SenseNovaLLMCompletionsRequest(
    val model: String,
    val prompt: String,
    val temperature: Float,
    val n: Int,
    val stream: Boolean,
    val stop: String,
    @SerialName("max_new_tokens")
    val maxTokens: Int
) {
    companion object {
        @JvmStatic
        private fun messagesToPrompt(messages: List<CodeRequest.Message>): String = messages.first().content

        @JvmStatic
        fun createFromCodeRequest(codeRequest: CodeRequest, stream: Boolean): SenseNovaLLMCompletionsRequest =
            SenseNovaLLMCompletionsRequest(
                codeRequest.model,
                messagesToPrompt(codeRequest.messages),
                codeRequest.temperature,
                codeRequest.n,
                stream,
                codeRequest.stop,
                codeRequest.maxTokens
            )
    }
}
