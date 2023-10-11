package com.sensetime.sensecore.sensecodeplugin.clients.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SenseNovaCodeRequest(
    val model: String,
    val messages: List<CodeRequest.Message>,
    val temperature: Float,
    val n: Int,
    val stream: Boolean,
    val stop: String,
    @SerialName("max_new_tokens")
    val maxTokens: Int
) {
    companion object {
        @JvmStatic
        fun makeSenseNovaCodeRequest(codeRequest: CodeRequest, stream: Boolean): SenseNovaCodeRequest =
            SenseNovaCodeRequest(
                codeRequest.model,
                codeRequest.messages,
                codeRequest.temperature,
                codeRequest.n,
                stream,
                codeRequest.stop,
                codeRequest.maxTokens
            )
    }
}
