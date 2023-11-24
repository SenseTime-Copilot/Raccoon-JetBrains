package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.credentialStore.Credentials
import com.sensetime.sensecode.jetbrains.raccoon.clients.models.PenroseModels
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMChatCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonCredentialsManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.letIfFilled
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toClientApiConfigMap
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toModelConfigMap
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class SenseCodeClient : CodeClient() {
    override val name: String
        get() = CLIENT_NAME

    override val webBaseUrl: String
        get() = baseUrl.replace("-api", "")

    override val userName: String?
        get() = accessUserName

    override val alreadyLoggedIn: Boolean
        get() = accessTokenCredentials?.letIfFilled { _, _ -> true } ?: false

    override val isSupportLogin: Boolean = true

    @Serializable
    private data class LoginData(
        val phone: String,
        val password: String,
        @SerialName("nation_code")
        val nationCode: String = "86"
    )

    private fun encrypt(src: ByteArray): String = Cipher.getInstance("AES/CFB/NoPadding").let { cipher ->
        cipher.init(
            Cipher.ENCRYPT_MODE,
            byteArrayOf(
                115,
                101,
                110,
                115,
                101,
                114,
                97,
                99,
                99,
                111,
                111,
                110,
                50,
                48,
                50,
                51
            ).let { pwd -> SecretKeySpec(pwd, "AES").also { Arrays.fill(pwd, 0) } })
        Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(src))
    }

    private fun cvtPhone(src: String): String = encrypt(src.toByteArray())
    private fun cvtPassword(src: CharArray): String =
        ByteArray(src.size) { src[it].code.toByte() }.let { pwd -> encrypt(pwd).also { Arrays.fill(pwd, 0) } }

    override suspend fun login(phone: String, password: CharArray) {
        val loginData = LoginData(cvtPhone(phone), cvtPassword(password))
        val loginJsonString = RaccoonClientJson.encodeToString(LoginData.serializer(), loginData)
        okHttpClient.newCall(
            createRequestBuilderWithCommonHeader(getApiEndpoint("/api/plugin/auth/v1/login_with_password")).post(
                loginJsonString.toRequestBody()
            ).build()
        ).await().let { response ->
            var bodyError: String? = null
            response.takeIf { it.isSuccessful }?.body?.let { responseBody ->
                RaccoonClientJson.decodeFromString(SenseCodeAuthResponse.serializer(), responseBody.string())
                    .let { authResponse ->
                        if (authResponse.hasError()) {
                            bodyError = authResponse.getShowError()
                            null
                        } else {
                            authResponse.data?.let { authData ->
                                updateLoginResult(authData.accessToken, authData.refreshToken)
                            }
                        }
                    }
            } ?: throw toErrorException(response) {
                bodyError ?: it?.body?.string()?.let { bodyString ->
                    RaccoonClientJson.decodeFromString(SenseCodeStatus.serializer(), bodyString).getShowError()
                }
            }
        }
    }

    private suspend fun getUserInfo(token: String?): String = okHttpClient.newCall(
        createRequestBuilderWithToken(getApiEndpoint("/api/plugin/auth/v1/user_info"), token).get().build()
    ).await().let { response ->
        var bodyError: String? = null
        response.takeIf { it.isSuccessful }?.body?.let { responseBody ->
            RaccoonClientJson.decodeFromString(SenseCodeUserInfoResponse.serializer(), responseBody.string())
                .let { userInfoResponse ->
                    if (userInfoResponse.hasError()) {
                        bodyError = userInfoResponse.getShowError()
                        null
                    } else {
                        userInfoResponse.data?.displayName.also {
                            accessUserName = it
                        }
                    }
                }
        } ?: throw toErrorException(response) {
            bodyError ?: it?.body?.string()?.let { bodyString ->
                RaccoonClientJson.decodeFromString(SenseCodeStatus.serializer(), bodyString).getShowError()
            }
        }
    }

    @Serializable
    private data class JWTPayload(val name: String, val exp: Int, val iat: Int? = null)

    private fun updateLoginResult(token: String, refresh: String?): Credentials? = kotlin.runCatching {
        token.split(".").getOrNull(1)?.let { payloadString ->
            val payloadObject: JWTPayload = RaccoonClientJson.decodeFromString(
                JWTPayload.serializer(),
                Base64.getUrlDecoder().decode(payloadString).decodeToString()
            )
            refresh?.let {
                refreshTokenCredentials = Credentials("${payloadObject.exp}", it)
            }
            Credentials(payloadObject.name, token).also { accessTokenCredentials = it }
        }
    }.getOrNull()

    override suspend fun logout() {
        try {
            okHttpClient.newCall(
                createRequestBuilderWithToken(getApiEndpoint("/api/plugin/auth/v1/logout")).delete().build()
            ).await()
        } catch (_: Throwable) {
        } finally {
            clearLoginToken()
        }
    }

    private abstract class SenseNovaClientApi : ClientApi {
        abstract fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String
        override fun addRequestBody(
            requestBuilder: Request.Builder,
            request: CodeRequest,
            stream: Boolean
        ): Request.Builder = requestBuilder.post(getRequestBodyJson(request, stream).toRequestBody())

        override fun toCodeResponse(body: String, stream: Boolean): CodeResponse =
            RaccoonClientJson.decodeFromString(SenseNovaCodeResponse.serializer(), body).toCodeResponse()
    }

    private class SenseNovaLLMChatCompletionsApi : SenseNovaClientApi() {
        override fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String =
            RaccoonClientJson.encodeToString(
                SenseNovaLLMChatCompletionsRequest.serializer(),
                SenseNovaLLMChatCompletionsRequest.createFromCodeRequest(request, stream)
            )
    }

    private class SenseNovaLLMCompletionsApi : SenseNovaClientApi() {
        override fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String =
            RaccoonClientJson.encodeToString(
                SenseNovaLLMCompletionsRequest.serializer(),
                SenseNovaLLMCompletionsRequest.createFromCodeRequest(request, stream)
            )
    }

    private val clientApiMap: Map<String, ClientApi> = mapOf(
        API_LLM_COMPLETIONS to SenseNovaLLMCompletionsApi(),
        API_LLM_CHAT_COMPLETIONS to SenseNovaLLMChatCompletionsApi()
    )

    override fun getClientApi(apiPath: String): ClientApi = clientApiMap.getValue(apiPath)

    @Serializable
    private data class RefreshRequest(
        @SerialName("refresh_token")
        private val refreshToken: String
    )

    private suspend fun checkRefreshToken(): Credentials? = kotlin.runCatching {
        refreshTokenCredentials?.letIfFilled { user, password ->
            user.toIntOrNull()?.takeIf { expiresIn -> ((Date().time / 1000L) + 15L) > expiresIn }
                ?.let { password }
        }?.let { refreshToken ->
            val refreshRequest = RefreshRequest(refreshToken)
            val refreshRequestJson = RaccoonClientJson.encodeToString(RefreshRequest.serializer(), refreshRequest)
            okHttpClient.newCall(
                createRequestBuilderWithCommonHeader(getApiEndpoint("/api/plugin/auth/v1/refresh")).post(
                    refreshRequestJson.toRequestBody()
                ).build()
            ).await().let { response ->
                var bodyError: String? = null
                response.takeIf { it.isSuccessful }?.body?.let { responseBody ->
                    RaccoonClientJson.decodeFromString(SenseCodeAuthResponse.serializer(), responseBody.string())
                        .let { authResponse ->
                            if (authResponse.hasError()) {
                                bodyError = authResponse.getShowError()
                                null
                            } else {
                                authResponse.data?.let { authData ->
                                    updateLoginResult(authData.accessToken, authData.refreshToken)
                                }
                            }
                        }
                } ?: throw toErrorException(response) {
                    bodyError ?: it?.body?.string()?.let { bodyString ->
                        RaccoonClientJson.decodeFromString(SenseCodeStatus.serializer(), bodyString).getShowError()
                    }
                }
            }
        }
    }.getOrNull()

    override suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder =
        (checkRefreshToken()?.getPasswordAsString() ?: accessToken)?.let {
            requestBuilder.addHeader(
                "Authorization",
                "Bearer $it"
            )
        } ?: throw UnauthorizedException("access token is empty")

    companion object {
        const val CLIENT_NAME = "sensecode"

        const val BASE_API = "https://code-api.sensetime.com"
//        const val BASE_API = "http://code-test-api.sensetime.com"

        private const val API_LLM_COMPLETIONS = "/api/plugin/nova/v1/proxy/v1/llm/completions"
        private const val API_LLM_CHAT_COMPLETIONS = "/api/plugin/nova/v1/proxy/v1/llm/chat-completions"
        private const val PTC_CODE_S_MODEL_NAME = "nova-ptc-s-v1-codecompletion"
        private const val PTC_CODE_L_MODEL_NAME = "nova-ptc-l-v1-code"

        private var accessTokenCredentials: Credentials?
            get() = RaccoonCredentialsManager.getAccessToken(CLIENT_NAME)
            set(value) {
                RaccoonCredentialsManager.setAccessToken(CLIENT_NAME, value)
            }
        private var accessUserName: String?
            get() = accessTokenCredentials?.letIfFilled { user, _ -> user }
            set(value) {
                if (value != accessTokenCredentials?.userName) {
                    accessTokenCredentials =
                        value?.letIfNotBlank { Credentials(it, accessTokenCredentials?.getPasswordAsString()) }
                }
            }
        private val accessToken: String?
            get() = accessTokenCredentials?.letIfFilled { _, password -> password }

        private var refreshTokenCredentials: Credentials?
            get() = RaccoonCredentialsManager.getRefreshToken(CLIENT_NAME)
            set(value) {
                RaccoonCredentialsManager.setRefreshToken(CLIENT_NAME, value)
            }

        @JvmStatic
        private fun clearLoginToken() {
            accessTokenCredentials = null
            refreshTokenCredentials = null
        }

        @JvmStatic
        private fun createRequestBuilderWithCommonHeader(apiEndpoint: String): Request.Builder =
            Request.Builder().url(apiEndpoint).header("Content-Type", "application/json")
                .addHeader("Date", getUTCDate())

        @JvmStatic
        private fun createRequestBuilderWithToken(
            apiEndpoint: String,
            token: String? = accessToken
        ): Request.Builder =
            token?.let {
                createRequestBuilderWithCommonHeader(apiEndpoint).addHeader("Authorization", "Bearer $it")
            } ?: throw UnauthorizedException("access token is empty")

        val defaultClientConfig: ClientConfig
            get() = ClientConfig(
                CLIENT_NAME,
                API_LLM_COMPLETIONS,
                API_LLM_CHAT_COMPLETIONS,
                listOf(
                    ClientConfig.ClientApiConfig(
                        API_LLM_COMPLETIONS,
                        PTC_CODE_S_MODEL_NAME,
                        listOf(PenroseModels.createModelCompletionSConfig(PTC_CODE_S_MODEL_NAME)).toModelConfigMap()
                    ),
                    ClientConfig.ClientApiConfig(
                        API_LLM_CHAT_COMPLETIONS,
                        PTC_CODE_L_MODEL_NAME,
                        listOf(PenroseModels.createModelChatLConfig(PTC_CODE_L_MODEL_NAME)).toModelConfigMap()
                    )
                ).toClientApiConfigMap()
            )
    }
}