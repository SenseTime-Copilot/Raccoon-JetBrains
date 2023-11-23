package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotEmpty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class SenseCodeStatus : Error {
    val code: Int? = null
    val message: String? = null

    override val error: String?
        get() = listOfNotNull(
            code?.takeIf { 0 != it }?.let { "code: $it" },
            message?.takeIf { it.isNotBlank() && ("ok" != it) && ("success" != it) }).takeIfNotEmpty()?.joinToString()
}

@Serializable
data class SenseCodeUserInfo(
    val phone: String,
    val name: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    val displayName: String = name ?: phone
}

@Serializable
data class SenseCodeUserInfoResponse(
    val data: SenseCodeUserInfo? = null
) : SenseCodeStatus()

@Serializable
data class SenseCodeAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null
)

@Serializable
data class SenseCodeAuthResponse(
    val data: SenseCodeAuth? = null
) : SenseCodeStatus()