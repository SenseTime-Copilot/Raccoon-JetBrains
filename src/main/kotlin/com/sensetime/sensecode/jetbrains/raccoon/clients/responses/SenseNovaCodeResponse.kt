package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotEmpty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SenseNovaChoice(
    override val index: Int? = null,
    val text: String? = null,
    val message: String? = null,
    val delta: String? = null,
    @SerialName("finish_reason")
    override val finishReason: String? = null
) : Choice {
    override val token: String?
        get() = message ?: text ?: delta
}

@Serializable
data class SenseNovaStatus(
    val code: Int? = null,
    val message: String? = null
) : Error {
    override val error: String?
        get() = listOfNotNull(
            code?.takeIf { 0 != it }?.let { "code: $it" },
            message?.takeIf { it.isNotBlank() && ("ok" != it) }).takeIfNotEmpty()?.joinToString()
}

@Serializable
data class SenseNovaCodeResponseData(
    override val id: String? = null,
    override val model: String? = null,
    override val `object`: String? = null,
    override val created: Int? = null,
    override val usage: UsageImpl? = null,
    override val choices: List<SenseNovaChoice>? = null,
    @SerialName("status")
    override var error: SenseNovaStatus? = null
) : CodeResponse

@Serializable
data class SenseNovaCodeResponse(
    val data: SenseNovaCodeResponseData? = null,
    val status: SenseNovaStatus? = null,
    @SerialName("error")
    val novaError: SenseNovaStatus? = null
) : SenseCodeStatus() {
    fun toCodeResponse(): CodeResponse = (data ?: SenseNovaCodeResponseData()).also { codeResponse ->
        if (hasError()) {
            codeResponse.error = SenseNovaStatus(code, message)
        }
        if (null == codeResponse.error?.takeIf { it.hasError() }) {
            codeResponse.error = this@SenseNovaCodeResponse.novaError ?: this@SenseNovaCodeResponse.status
        }
    }
}