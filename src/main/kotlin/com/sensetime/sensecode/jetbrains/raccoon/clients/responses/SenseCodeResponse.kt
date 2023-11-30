package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class SenseCodeStatus : Error {
    val code: Int? = null
    val message: String? = null
    private val details: String? = null
    private val errorParamName: String? = details?.let {
        Regex("param\\s+(\\w+)").find(it)?.groupValues?.getOrNull(1)
            ?.let { paramName -> paramNamesMap.getOrDefault(paramName, paramName) }
    }

    override val error: String?
        get() = code?.takeIf { 0 != it }?.let { c ->
            when (c) {
                100001 -> errorParamName?.let {
                    RaccoonBundle.message(
                        "client.sensecode.response.error.paramEmpty",
                        it
                    )
                }

                100002 -> errorParamName?.let {
                    RaccoonBundle.message(
                        "client.sensecode.response.error.paramInvalid",
                        it
                    )
                }

                100008, 200103 -> RaccoonBundle.message("client.sensecode.response.error.requestLimit")
                200002, 200003 -> RaccoonBundle.message("client.sensecode.response.error.authFailed")
                200004 -> RaccoonBundle.message("client.sensecode.response.error.invalidPhoneOrPassword")
                200005 -> RaccoonBundle.message("client.sensecode.response.error.userNotFound")
                200007 -> RaccoonBundle.message("client.sensecode.response.error.userLocked")
                200101 -> RaccoonBundle.message("client.sensecode.response.error.tryLoginLimit")
                else -> null
            } ?: message?.takeIf { m -> m.isNotBlank() && ("ok" != m) && ("success" != m) }?.let { details ?: it }
        }

    companion object {
        private val paramNamesMap: Map<String, String> = mapOf(
            "phone" to RaccoonBundle.message("login.dialog.label.phone"),
            "password" to RaccoonBundle.message("login.dialog.label.password")
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