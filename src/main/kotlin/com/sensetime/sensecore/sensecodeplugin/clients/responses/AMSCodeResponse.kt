package com.sensetime.sensecore.sensecodeplugin.clients.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AMSRespMessage(val role: String? = null, val content: String? = null)

@Serializable
data class AMSChoice(
    override val index: Int? = null,
    val message: AMSRespMessage? = null,
    @SerialName("finish_reason")
    override val finishReason: String? = null
) : Choice {
    override val token: String?
        get() = message?.content?.takeUnless { it.contains("<|text|>") or it.contains("<|endofblock|>") }
}

@Serializable
data class AMSDetail(
    @SerialName("@type")
    val type: String? = null,
    val reason: String? = null,
    val domain: String? = null,
)

@Serializable
data class AMSError(
    val code: Int? = null,
    val type: String? = null,
    val message: String? = null,
    val details: List<AMSDetail>? = null
) : Error {
    override val error: String
        get() = listOfNotNull(
            code?.takeIf { it != 0 }?.let { "code: \"$it\"" },
            type?.takeIf { it.isNotBlank() }?.let { "type: \"$it\"" },
            message?.takeIf { it.isNotBlank() }).joinToString()
}

@Serializable
data class AMSCodeResponse(
    override val id: String? = null,
    override val model: String? = null,
    override val `object`: String? = null,
    override val created: Int? = null,
    override val usage: UsageImpl? = null,
    override val choices: List<AMSChoice>? = null,
    override val error: AMSError? = null
) : CodeResponse