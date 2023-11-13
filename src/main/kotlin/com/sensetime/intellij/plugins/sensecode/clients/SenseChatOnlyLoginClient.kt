package com.sensetime.intellij.plugins.sensecode.clients

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.intellij.credentialStore.Credentials
import com.sensetime.intellij.plugins.sensecode.clients.models.PenroseModels
import com.sensetime.intellij.plugins.sensecode.persistent.SenseCodeCredentialsManager
import com.sensetime.intellij.plugins.sensecode.persistent.letIfFilled
import com.sensetime.intellij.plugins.sensecode.services.authentication.SenseChatAuthService
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import com.sensetime.intellij.plugins.sensecode.clients.requests.CodeRequest
import com.sensetime.intellij.plugins.sensecode.clients.requests.SenseNovaLLMChatCompletionsRequest
import com.sensetime.intellij.plugins.sensecode.clients.requests.SenseNovaLLMCompletionsRequest
import com.sensetime.intellij.plugins.sensecode.clients.responses.CodeResponse
import com.sensetime.intellij.plugins.sensecode.clients.responses.SenseChatCheckTokenResponse
import com.sensetime.intellij.plugins.sensecode.clients.responses.SenseChatRefreshTokenResponse
import com.sensetime.intellij.plugins.sensecode.clients.responses.SenseNovaCodeResponse
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ClientConfig
import com.sensetime.intellij.plugins.sensecode.persistent.settings.toClientApiConfigMap
import com.sensetime.intellij.plugins.sensecode.persistent.settings.toModelConfigMap
import com.sensetime.intellij.plugins.sensecode.utils.letIfNotBlank

class SenseChatOnlyLoginClient : CodeClient() {
    override val name: String
        get() = CLIENT_NAME

    private val senseChatBaseUrl: String
        get() = "https://chat${getEnvFromNovaBaseUrl(baseUrl)}.sensetime.com"

    override val userName: String?
        get() = accessUserName

    override val alreadyLoggedIn: Boolean
        get() = accessTokenCredentials?.letIfFilled { _, _ -> true } ?: false

    override val isSupportLogin: Boolean = true

    override suspend fun login() {
        SenseChatAuthService.Util.startLoginFromBrowser("${senseChatBaseUrl}/wb/login")
    }

    private suspend fun checkSenseChatTokenAndGetUserName(token: String?): String = okHttpClient.newCall(
        createRequestBuilderWithToken("${senseChatBaseUrl}/api/auth/v1.0.4/check", token).get().build()
    ).await().let { response ->
        var bodyError: String? = null
        response.takeIf { it.isSuccessful }?.body?.let { responseBody ->
            SenseCodeClientJson.decodeFromString(SenseChatCheckTokenResponse.serializer(), responseBody.string())
                .let { checkTokenResponse ->
                    bodyError = checkTokenResponse.displayError
                    checkTokenResponse.displayUserName.also {
                        accessUserName = it
                    }
                }
        } ?: throw toErrorException(response) { bodyError }
    }

    @Serializable
    private data class JWTPayload(val exp: Int, val iat: Int? = null)

    suspend fun updateLoginResult(token: String, refresh: String?): Credentials? = kotlin.runCatching {
        checkSenseChatTokenAndGetUserName(token).let { userName ->
            token.split(".").getOrNull(1)?.let { payloadString ->
                val payloadObject: JWTPayload = SenseCodeClientJson.decodeFromString(
                    JWTPayload.serializer(),
                    Base64.getUrlDecoder().decode(payloadString).decodeToString()
                )
                refresh?.let {
                    refreshTokenCredentials = Credentials("${payloadObject.exp}", it)
                }
                Credentials(userName, token).also { accessTokenCredentials = it }
            }
        }
    }.getOrNull()

    override suspend fun logout() {
        try {
            okHttpClient.newCall(
                createRequestBuilderWithToken("${senseChatBaseUrl}/api/auth/v1.0.2/logout").post("{}".toRequestBody())
                    .build()
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
            SenseCodeClientJson.decodeFromString(SenseNovaCodeResponse.serializer(), body).toCodeResponse()
    }

    private class SenseNovaLLMChatCompletionsApi : SenseNovaClientApi() {
        override fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String =
            SenseCodeClientJson.encodeToString(
                SenseNovaLLMChatCompletionsRequest.serializer(),
                SenseNovaLLMChatCompletionsRequest.createFromCodeRequest(request, stream)
            )
    }

    private class SenseNovaLLMCompletionsApi : SenseNovaClientApi() {
        override fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String =
            SenseCodeClientJson.encodeToString(
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
        private val access: String,
        private val refresh: String
    )

    private suspend fun checkRefreshToken(): Credentials? = kotlin.runCatching {
        accessToken?.letIfNotBlank { currentAccessToken ->
            refreshTokenCredentials?.letIfFilled { user, password ->
                user.toIntOrNull()?.takeIf { expiresIn -> ((Date().time / 1000L) + 60L) > expiresIn }
                    ?.let { password }
            }?.let { refreshToken ->
                val refreshRequest = RefreshRequest(currentAccessToken, refreshToken)
                val refreshRequestJson = SenseCodeClientJson.encodeToString(RefreshRequest.serializer(), refreshRequest)
                okHttpClient.newCall(
                    createRequestBuilderWithCommonHeader("${senseChatBaseUrl}/api/auth/v1.0.4/refresh").post(
                        refreshRequestJson.toRequestBody()
                    ).build()
                ).await().let { response ->
                    response.takeIf { it.isSuccessful }?.body?.let { body ->
                        SenseCodeClientJson.decodeFromString(SenseChatRefreshTokenResponse.serializer(), body.string())
                            .takeIf { it.isOk }?.let { refreshResponse ->
                                updateLoginResult(refreshResponse.access, refreshResponse.refresh)
                            }
                    }
                }
            }
        }
    }.getOrNull()

    private fun getAuthorizationFromAkSk(): String =
        JWT.create().withIssuer("2SVR6XdhiZ8hvUTwI2AHVjdT1FH").withHeader(mapOf("alg" to "HS256"))
            .withExpiresAt(Date(System.currentTimeMillis() + 1800 * 1000))
            .withNotBefore(Date(System.currentTimeMillis() - 5 * 1000))
            .sign(Algorithm.HMAC256("4rPbIhmiuRK4pZiT8ddbLn0Mr7SVeZ4r"))

    override suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder =
        (checkRefreshToken()?.userName ?: checkSenseChatTokenAndGetUserName(accessToken)).let {
            requestBuilder.addHeader(
                "Authorization",
                "Bearer ${getAuthorizationFromAkSk()}"
            )
        }

    companion object {
        const val CLIENT_NAME = "sensechat-nova"
        const val BASE_API = "https://api.sensenova.cn"

        private const val API_LLM_COMPLETIONS = "/v1/llm/completions"
        private const val API_LLM_CHAT_COMPLETIONS = "/v1/llm/chat-completions"
        private const val PTC_CODE_S_MODEL_NAME = "nova-ptc-s-v1-codecompletion"
        private const val PTC_CODE_L_MODEL_NAME = "nova-ptc-l-v1-code"

        private var accessTokenCredentials: Credentials?
            get() = SenseCodeCredentialsManager.getAccessToken(CLIENT_NAME)
            set(value) {
                SenseCodeCredentialsManager.setAccessToken(CLIENT_NAME, value)
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
            get() = SenseCodeCredentialsManager.getRefreshToken(CLIENT_NAME)
            set(value) {
                SenseCodeCredentialsManager.setRefreshToken(CLIENT_NAME, value)
            }

        @JvmStatic
        private fun clearLoginToken() {
            accessTokenCredentials = null
            refreshTokenCredentials = null
        }

        @JvmStatic
        private fun getEnvFromNovaBaseUrl(novaBaseUrl: String): String {
            return ""
            val envPrefix = "api."
            val envPostFix = ".sensenova."
            var startIndex = novaBaseUrl.indexOf(envPrefix)
            if (startIndex >= 0) {
                startIndex += envPrefix.length
                val endIndex = novaBaseUrl.indexOf(envPostFix, startIndex)
                if (endIndex > startIndex) {
                    return "-${novaBaseUrl.substring(startIndex, endIndex)}"
                }
            }
            return ""
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