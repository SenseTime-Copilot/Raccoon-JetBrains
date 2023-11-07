package com.sensetime.intellij.plugins.sensecode.clients.responses

import com.sensetime.intellij.plugins.sensecode.utils.letIfNotBlank
import com.sensetime.intellij.plugins.sensecode.utils.takeIfNotBlank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SenseChatAuthResponse {
    val code: Int?
    val error: String?

    val isOk: Boolean
        get() = displayError.isNullOrBlank()
    val requestDisplayType: String
    val displayError: String?
        get() = (error?.takeIfNotBlank() ?: (code?.takeIf { 0 != it }
            ?.let { "code($it) is not zero" }))?.letIfNotBlank { "$requestDisplayType: $it" }
}

@Serializable
data class SenseChatCheckTokenResponse(
    override val code: Int? = null,
    override val error: String? = null,
    @SerialName("user_id")
    val userID: Int? = null,
    @SerialName("neckname")
    val neckName: String? = null,
    @SerialName("user_name")
    val userName: String? = null,
    val mobile: String? = null,
    val email: String? = null
) : SenseChatAuthResponse {
    override val requestDisplayType: String
        get() = "SenseChatCheckToken"

    val displayUserName: String?
        get() = takeIf { it.isOk }?.run {
            neckName?.takeIfNotBlank() ?: userName?.takeIfNotBlank() ?: email?.takeIfNotBlank()?.substringBefore('@')
            ?: mobile?.takeIf { 11 == it.length }?.replaceRange(3, 7, "****") ?: "sensechat user"
        }
}

@Serializable
data class SenseChatRefreshTokenResponse(
    @SerialName("access")
    val access: String,
    @SerialName("refresh")
    val refresh: String? = null,
    override val code: Int? = null,
    override val error: String? = null
) : SenseChatAuthResponse {
    override val requestDisplayType: String
        get() = "SenseChatRefreshToken"
}