package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.credentialStore.Credentials
import com.sensetime.sensecore.sensecodeplugin.actions.task.*
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.requests.SenseNovaCodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.CodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.SenseNovaCodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.SenseNovaStatus
import com.sensetime.sensecore.sensecodeplugin.i18n.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.services.http.authentication.SenseNovaAuthService
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeCredentialsManager
import com.sensetime.sensecore.sensecodeplugin.settings.letIfFilled
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*

class SenseNovaClient : CodeClient() {
    override val name: String = CLIENT_NAME

    override val userName: String?
        get() = accessToken.letIfFilled { user, _ -> user } ?: aksk.letIfFilled { _, _ -> "$name ak/sk user" }

    override val isLogin: Boolean
        get() = accessToken.letIfFilled { _, _ -> true } ?: false

    override val isSupportLogin: Boolean = true

    override suspend fun login(apiEndpoint: String) {
        SenseNovaAuthService.Util.startLoginFromBrowser(
            getLoginUrl(getEnvFromApiEndpoint(apiEndpoint)),
            TOKEN_EXPIRES_AFTER
        )
    }

    override suspend fun logout(apiEndpoint: String) {
        try {
            kotlin.runCatching {
                addAuthorization(
                    Request.Builder().url(getLogoutUrl(getEnvFromApiEndpoint(apiEndpoint)))
                        .header("Content-Type", "application/json").addHeader("Date", getUTCDate()), apiEndpoint
                )
            }.onSuccess { client.newCall(it.get().build()).await() }
        } finally {
            SenseCodeCredentialsManager.setClientAuth(CLIENT_NAME, ACCESS_TOKEN_KEY)
            SenseCodeCredentialsManager.setClientAuth(CLIENT_NAME, REFRESH_TOKEN_KEY)
        }
    }

    override suspend fun addAuthorization(requestBuilder: Request.Builder, apiEndpoint: String): Request.Builder =
        getAccessToken(getEnvFromApiEndpoint((apiEndpoint)))?.letIfFilled { _, password ->
            requestBuilder.addHeader("Authorization", "Bearer $password")
        } ?: throw UnauthorizedException(name, "access token is empty")

    override fun addPostBody(
        requestBuilder: Request.Builder, request: CodeRequest, stream: Boolean
    ): Request.Builder {
        val novaRequest = SenseNovaCodeRequest.makeSenseNovaCodeRequest(request, stream)
        val novaRequestJson = SenseCodeClientJson.encodeToString(SenseNovaCodeRequest.serializer(), novaRequest)
        return requestBuilder.post(novaRequestJson.toRequestBody())
    }

    override fun toCodeResponse(body: String, stream: Boolean): CodeResponse =
        SenseCodeClientJson.decodeFromString(SenseNovaCodeResponse.serializer(), body).data

    @Serializable
    private data class RefreshRequest(
        @SerialName("grant_type")
        private val grantType: String,
        @SerialName("refresh_token")
        private val refreshToken: String,
        @SerialName("expires_after")
        private val expiresAfter: Int
    )

    @Serializable
    private data class RefreshResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("refresh_token")
        val refreshToken: String,
        @SerialName("expires_in")
        val expiresIn: Int,
        val status: SenseNovaStatus? = null
    )

    private suspend fun checkRefreshToken(env: String): Credentials? = kotlin.runCatching {
        accessToken?.userName?.takeIf { it.isNotBlank() }?.let { currentUserName ->
            refreshToken?.letIfFilled { user, password ->
                user.toIntOrNull()?.takeIf { expiresIn -> ((Date().time / 1000L) + (3600L * 24)) > expiresIn }
                    ?.let { password }
            }?.let { refreshToken ->
                val refreshRequest = RefreshRequest("refresh", refreshToken, TOKEN_EXPIRES_AFTER)
                val refreshRequestJson = SenseCodeClientJson.encodeToString(RefreshRequest.serializer(), refreshRequest)
                client.newCall(
                    Request.Builder().url(getRefreshTokenUrl(env)).header("Content-Type", "application/json")
                        .addHeader("Date", getUTCDate()).post(refreshRequestJson.toRequestBody()).build()
                ).await().let { response ->
                    response.takeIf { it.isSuccessful }?.body?.let { body ->
                        SenseCodeClientJson.decodeFromString(RefreshResponse.serializer(), body.string())
                            .takeIf { (null == it.status) || !it.status.hasError() }?.let { refreshResponse ->
                                SenseCodeCredentialsManager.setClientAuth(
                                    CLIENT_NAME,
                                    REFRESH_TOKEN_KEY,
                                    Credentials("${refreshResponse.expiresIn}", refreshResponse.refreshToken)
                                )
                                Credentials(currentUserName, refreshResponse.accessToken).also {
                                    SenseCodeCredentialsManager.setClientAuth(CLIENT_NAME, ACCESS_TOKEN_KEY, it)
                                }
                            }
                    }
                }
            }
        }
    }.getOrNull()

    private suspend fun getAccessToken(env: String): Credentials? = checkRefreshToken(env) ?: accessToken

    companion object {
        const val CLIENT_NAME = "sensenova"
        private const val AKSK_KEY = "aksk"
        private const val REFRESH_TOKEN_KEY = "refreshToken"
        private const val ACCESS_TOKEN_KEY = "accessToken"
        private const val TOKEN_EXPIRES_AFTER = 3600 * 24 * 7

        private const val PTC_CODE_S_MODEL_NAME = "novs-ptc-s-v1-code"

        private val aksk: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, AKSK_KEY)
        private val refreshToken: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, REFRESH_TOKEN_KEY)
        private val accessToken: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, ACCESS_TOKEN_KEY)

        @Serializable
        private data class JWTPayload(val email: String? = null, val exp: Int? = null)

        @JvmStatic
        fun updateLoginResult(token: String, refresh: String, expires: Int?) {
            token.split(".").getOrNull(1)?.let { payloadString ->
                val payloadObject: JWTPayload = SenseCodeClientJson.decodeFromString(
                    JWTPayload.serializer(),
                    Base64.getUrlDecoder().decode(payloadString).decodeToString()
                )
                val user: String =
                    payloadObject.email?.takeIf { it.isNotBlank() }?.substringBefore('@') ?: "$CLIENT_NAME user"
                SenseCodeCredentialsManager.setClientAuth(CLIENT_NAME, ACCESS_TOKEN_KEY, Credentials(user, token))
                SenseCodeCredentialsManager.setClientAuth(
                    CLIENT_NAME,
                    REFRESH_TOKEN_KEY,
                    Credentials("${payloadObject.exp ?: expires}", refresh)
                )
            }
        }

        @JvmStatic
        private fun getCodeTaskActionPrompt(taskType: String, custom: String = ""): String =
            "\n### Instruction:\nTask type: ${taskType}. ${SenseCodeBundle.message("completions.task.prompt.penrose.explanation")}.${custom}\n\n### Input:\n{code}\n"

        @JvmStatic
        private fun makePTCCodeSModelConfig(): ModelConfig {
            val maxInputTokens = 4096
            val tokenLimit = 8192
            return ModelConfig(
                PTC_CODE_S_MODEL_NAME, 0.5f, "<|end|>", maxInputTokens, tokenLimit, mapOf(
                    CodeTaskActionBase.getActionKey(GenerationAction::class) to ModelConfig.PromptTemplate(
                        getCodeTaskActionPrompt("code generation")
                    ),
                    CodeTaskActionBase.getActionKey(AddTestAction::class) to ModelConfig.PromptTemplate(
                        getCodeTaskActionPrompt("test sample generation")
                    ),
                    CodeTaskActionBase.getActionKey(CodeConversionAction::class) to ModelConfig.PromptTemplate(
                        getCodeTaskActionPrompt(
                            "code language conversion",
                            SenseCodeBundle.message("completions.task.prompt.penrose.language.convert")
                        )
                    ),
                    CodeTaskActionBase.getActionKey(CodeCorrectionAction::class) to ModelConfig.PromptTemplate(
                        getCodeTaskActionPrompt("code error correction")
                    ),
                    CodeTaskActionBase.getActionKey(RefactoringAction::class) to ModelConfig.PromptTemplate(
                        getCodeTaskActionPrompt("code refactoring and optimization")
                    )
                ), ModelConfig.PromptTemplate("{content}"), mapOf(), mapOf(
                    "middle" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_suffix>{suffix}<fim_middle>"),
                    "end" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_middle><fim_suffix>")
                ), mapOf(
                    ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
                    ModelConfig.CompletionPreference.BALANCED to 256,
                    ModelConfig.CompletionPreference.BEST_EFFORT to (tokenLimit - maxInputTokens)
                )
            )
        }

        @JvmStatic
        fun getDefaultClientConfig(): ClientConfig = ClientConfig(
            CLIENT_NAME,
            ::SenseNovaClient,
            PTC_CODE_S_MODEL_NAME,
            PTC_CODE_S_MODEL_NAME,
            PTC_CODE_S_MODEL_NAME,
            PTC_CODE_S_MODEL_NAME,
            "https://api.sensenova.cn/v1/llm/code/chat-completions",
            mapOf(PTC_CODE_S_MODEL_NAME to makePTCCodeSModelConfig())
        )


        @JvmStatic
        private fun getEnvFromApiEndpoint(apiEndpoint: String): String {
            val startIndex = apiEndpoint.indexOf(".sensenova.")
            if (startIndex >= 0) {
                val endIndex = apiEndpoint.indexOf('/', startIndex)
                if (endIndex > startIndex) {
                    return apiEndpoint.substring(startIndex, endIndex)
                }
            }
            return "cn"
        }

        @JvmStatic
        private fun getLoginUrl(env: String): String = "https://login.sensenova.$env/#/login"

        @JvmStatic
        private fun getLogoutUrl(env: String): String = "https://iam-login.sensenova.$env/sensenova-sso/v1/logout"

        @JvmStatic
        private fun getRefreshTokenUrl(env: String): String = "https://iam-login.sensenova.$env/sensenova-sso/v1/token"
    }
}