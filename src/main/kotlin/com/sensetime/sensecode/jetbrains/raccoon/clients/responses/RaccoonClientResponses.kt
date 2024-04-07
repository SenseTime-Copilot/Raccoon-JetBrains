package com.sensetime.sensecode.jetbrains.raccoon.clients.responses

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientMessageException
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientUnauthorizedException
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.getNameFromEmail
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal abstract class RaccoonClientStatus(
    private val details: String? = null
) : ClientCodeStatus() {
    override fun getDetailsInfo(): String? = details

    override fun throwIfError() {
        takeIfCodeNotOk()?.let { c ->
            when (c) {
                EMPTY_PARAMS_CODE -> getErrorParamName()?.let {
                    RaccoonBundle.message("client.sensecode.response.error.paramEmpty", it)
                }

                INVALID_PARAMS_CODE -> getErrorParamName()?.let {
                    RaccoonBundle.message("client.sensecode.response.error.paramInvalid", it)
                }

                INVALID_AUTHORIZATION_CODE, AUTHORIZATION_VERIFY_ERROR_CODE -> throw LLMClientUnauthorizedException(
                    takeIfMessageNotBlankOrOk(), getDetailsInfo()
                )

                REQUEST_EXCEEDED_LIMIT_CODE, REQUEST_EXCEEDED_MAX_TIME_CODE -> RaccoonBundle.message("client.sensecode.response.error.requestLimit")
                PHONE_OR_PASSWD_VERIFY_ERROR_CODE -> RaccoonBundle.message("client.sensecode.response.error.invalidPhoneOrPassword")
                USER_NOT_FOUND_CODE -> RaccoonBundle.message("client.sensecode.response.error.userNotFound")
                USER_APPLICATION_REVIEWING_CODE -> RaccoonBundle.message("client.sensecode.response.error.userApplicationReview")
                USER_APPLICATION_REJECTED_CODE -> RaccoonBundle.message("client.sensecode.response.error.userApplicationRejected")
                USER_DISABLED_CODE -> RaccoonBundle.message("client.sensecode.response.error.userDisabled")
                EMAIL_OR_PASSWD_VERIFY_ERROR_CODE -> RaccoonBundle.message("client.sensecode.response.error.invalidEmailOrPassword")
                USER_VERIFY_EXCEEDED_LIMIT_CODE -> RaccoonBundle.message("client.sensecode.response.error.tryLoginLimit")
                EMAIL_VERIFY_EXCEEDED_LIMIT_CODE -> RaccoonBundle.message("client.sensecode.response.error.tryLoginLimit.email")
                else -> null
            }?.let { message -> throw LLMClientMessageException(message, getDetailsInfo()) }
        }
        super.throwIfError()
    }

    private fun getErrorParamName(): String? = details?.let {
        Regex("param\\s+(\\w+)").find(it)?.groupValues?.getOrNull(1)
            ?.let { paramName -> paramNamesMap.getOrDefault(paramName, paramName) }
    }

    companion object {
        private const val EMPTY_PARAMS_CODE = 100001
        private const val INVALID_PARAMS_CODE = 100002
        private const val REQUEST_EXCEEDED_LIMIT_CODE = 100008
        private const val REQUEST_EXCEEDED_MAX_TIME_CODE = 200103
        private const val INVALID_AUTHORIZATION_CODE = 200002
        private const val AUTHORIZATION_VERIFY_ERROR_CODE = 200003
        private const val PHONE_OR_PASSWD_VERIFY_ERROR_CODE = 200004
        private const val USER_NOT_FOUND_CODE = 200005
        private const val USER_APPLICATION_REVIEWING_CODE = 200007
        private const val USER_APPLICATION_REJECTED_CODE = 200008
        private const val USER_DISABLED_CODE = 200009
        private const val EMAIL_OR_PASSWD_VERIFY_ERROR_CODE = 200013
        private const val USER_VERIFY_EXCEEDED_LIMIT_CODE = 200101
        private const val EMAIL_VERIFY_EXCEEDED_LIMIT_CODE = 200105

        private val paramNamesMap: Map<String, String> = mapOf(
            "phone" to RaccoonBundle.message("login.dialog.label.phone"),
            "password" to RaccoonBundle.message("login.dialog.label.password"),
            "email" to RaccoonBundle.message("login.dialog.label.email")
        )
    }
}

@Serializable
internal data class RaccoonClientLLMResponse<T : NovaClientLLMChoice>(
    val status: NovaClientStatus? = null,
    val error: NovaClientStatus? = null,
    val data: NovaClientLLMResponseData<T> = NovaClientLLMResponseData()
) : RaccoonClientStatus(), LLMResponseData<T> by data, LLMResponse<T> {
    override fun throwIfError() {
        super.throwIfError()
        data.status?.throwIfError()
        error?.throwIfError()
        status?.throwIfError()
    }
}
internal typealias RaccoonClientLLMCompletionResponse = RaccoonClientLLMResponse<NovaClientLLMCompletionChoice>
internal typealias RaccoonClientLLMChatResponse = RaccoonClientLLMResponse<NovaClientLLMChatChoice>
internal typealias RaccoonClientLLMAgentResponse = RaccoonClientLLMResponse<NovaClientLLMAgentChoice>


// other responses

@Serializable
internal data class RaccoonClientOrgInfo(
    val code: String,
    val name: String? = null,
    @SerialName("user_name")
    val userName: String? = null,
    @SerialName("user_role")
    val userRole: String? = null,
    @SerialName("user_status")
    val userStatus: String? = null,
    @SerialName("team_code_enabled")
    val teamCodeEnabled: Boolean = false,
    @SerialName("team_code_expired_time")
    val teamCodeExpiredTime: String? = null
) {
    val displayName: String
        get() = name.ifNullOrBlank(code)

    fun isNormal(): Boolean = ("normal" == userStatus)
    fun isAvailable(): Boolean = teamCodeEnabled && isNormal()
}

@Serializable
internal data class RaccoonClientUserInfo(
    @SerialName("created_at")
    val createdAt: String? = null,
    val name: String? = null,
//    @SerialName("nation_code")
//    val nationCode: String? = null,
//    val phone: String? = null,
    val email: String? = null,
    @SerialName("pro_code_enabled")
    val proCodeEnabled: Boolean = false,
    @SerialName("pro_code_expired_time")
    val proCodeExpiredTime: String? = null,
    @SerialName("orgs")
    val organizations: List<RaccoonClientOrgInfo>? = null
) {
    fun getFirstAvailableOrganizationOrNull(): RaccoonClientOrgInfo? = organizations?.firstOrNull { it.isAvailable() }
    fun getDisplayName(): String = name?.takeIfNotBlank() ?: email?.getNameFromEmail()?.takeIfNotBlank() ?: "unknown"
}

@Serializable
internal data class RaccoonClientUserInfoResponse(
    val data: RaccoonClientUserInfo? = null
) : RaccoonClientStatus()

@Serializable
internal data class RaccoonClientTokens(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null
)

@Serializable
internal data class RaccoonClientTokensResponse(
    val data: RaccoonClientTokens? = null
) : RaccoonClientStatus()

@Serializable
internal data class RaccoonClientSensitiveConversation(
    @SerialName("turn_id")
    val id: String = "",
    @SerialName("sensetive_type")
    override val type: String? = null
) : RaccoonSensitiveListener.SensitiveConversation

@Serializable
internal data class RaccoonClientSensitives(
    val list: List<RaccoonClientSensitiveConversation> = emptyList()
)

internal fun List<RaccoonClientSensitiveConversation>.toSensitiveConversationMap(): Map<String, RaccoonSensitiveListener.SensitiveConversation> =
    filter { it.id.isNotBlank() }.associateBy(RaccoonClientSensitiveConversation::id)

@Serializable
internal data class RaccoonClientSensitivesResponse(
    val data: RaccoonClientSensitives? = null
) : RaccoonClientStatus()
