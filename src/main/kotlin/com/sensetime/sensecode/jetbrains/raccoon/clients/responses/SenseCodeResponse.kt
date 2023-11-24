package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class SenseCodeStatus : Error {
    val code: Int? = null
    val message: String? = null

    override val error: String?
        get() = code?.takeIf { 0 != it }?.let { c ->
            errorMessagesMap.getOrDefault(
                c,
                message?.takeIf { m -> m.isNotBlank() && ("ok" != m) && ("success" != m) })
        }

    companion object {
        private val errorMessagesMap: Map<Int, String> = mapOf(
            200004 to RaccoonBundle.message("client.sensecode.response.error.invalidPhoneOrPassword"),
            200005 to RaccoonBundle.message("client.sensecode.response.error.UserNotFound"),
            200003 to RaccoonBundle.message("client.sensecode.response.error.authFailed")
        )
    }
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