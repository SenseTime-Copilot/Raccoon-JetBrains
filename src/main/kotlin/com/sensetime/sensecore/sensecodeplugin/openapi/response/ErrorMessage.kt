package com.sensetime.sensecore.sensecodeplugin.openapi.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: Int? = null,
    val message: String? = null,
    val details: List<ErrorDetails>? = null,
)

@Serializable
data class ErrorDetails(
    @SerialName("@type")
    val type: String? = null,
    val domain: String? = null,
    val reason: String? = null
)
