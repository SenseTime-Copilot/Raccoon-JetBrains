package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.sensetime.sensecode.jetbrains.raccoon.clients.models.PenroseModels
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.BehaviorMetrics
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMChatCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonCredentialsManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.letIfFilled
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toClientApiConfigMap
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toModelConfigMap
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_SENSITIVE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

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
        val nationCode: String
    )

    @Serializable
    private data class LoginEmailData(
        val email: String,
        val password: String
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

    override suspend fun login(nationCode: String, phone: String, password: CharArray) {
        val loginData = LoginData(cvtPhone(phone), cvtPassword(password), nationCode)
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

    override suspend fun login(email: String, password: CharArray) {
        val loginData = LoginEmailData(email, cvtPassword(password))
        val loginJsonString = RaccoonClientJson.encodeToString(LoginEmailData.serializer(), loginData)
        okHttpClient.newCall(
            createRequestBuilderWithCommonHeader(getApiEndpoint("/api/plugin/auth/v1/login_with_email_password")).post(
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
                createRequestBuilderWithToken(getApiEndpoint("/api/plugin/auth/v1/logout")).post("{}".toRequestBody())
                    .build()
            ).await()
        } catch (_: Throwable) {
        } finally {
            clearLoginToken()
        }
    }

    private var sensitiveJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var lastSensitiveTime = AtomicLong(RaccoonUtils.getSystemTimestampMs())
    override fun onOkResponse(response: Response) {
        response.headers("x-raccoon-sensetive").firstOrNull()?.toLongOrNull()?.let { currentSensitiveTime ->
            val startTime = lastSensitiveTime.get()
            if ((currentSensitiveTime * 1000L) > startTime) {
                val tmpTime = RaccoonUtils.getSystemTimestampMs()
                sensitiveJob = RaccoonClientManager.launchClientJob {
                    kotlin.runCatching {
                        val sensitives = getSensitiveConversations(startTime.toString(), action = "response header")
                        lastSensitiveTime.set(tmpTime)
                        if (sensitives.isNotEmpty()) {
                            ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_SENSITIVE_TOPIC)
                                .onNewSensitiveConversations(sensitives)
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) {
                            throw e
                        }
                    }
                }
            }
        }
    }

    private fun buildSensitiveGetUrl(
        startTime: String,
        endTime: String?
    ): String = getApiEndpoint("/api/plugin/sensetive/v1/sensetives").toHttpUrl().newBuilder().run {
        addQueryParameter("start_time", startTime)
        endTime?.letIfNotBlank { addQueryParameter("end_time", it) }
        RaccoonUtils.machineID?.letIfNotBlank { addQueryParameter("machine_id", it) }
        build().toString()
    }

    override suspend fun getSensitiveConversations(
        startTime: String,
        endTime: String?,
        action: String
    ): Map<String, RaccoonSensitiveListener.SensitiveConversation> = okHttpClient.newCall(
        addAuthorizationWithCheckRefreshToken(
            createRequestBuilderWithCommonHeader(
                buildSensitiveGetUrl(
                    startTime,
                    endTime
                )
            )
        ).apply {
            appendCommonRaccoonHeader(this, null, action)
        }.get().build()
    ).await().let { response ->
        var bodyError: String? = null
        response.takeIf { it.isSuccessful }?.body?.let { responseBody ->
            RaccoonClientJson.decodeFromString(SenseCodeSensitiveResponse.serializer(), responseBody.string())
                .let { sensitiveResponse ->
                    if (sensitiveResponse.hasError()) {
                        bodyError = sensitiveResponse.getShowError()
                        null
                    } else {
                        sensitiveResponse.data?.list?.toSensitiveConversationMap()
                    }
                }
        } ?: throw toErrorException(response) {
            bodyError ?: it?.body?.string()?.let { bodyString ->
                RaccoonClientJson.decodeFromString(SenseCodeStatus.serializer(), bodyString).getShowError()
            }
        }
    }

    override suspend fun uploadBehaviorMetrics(behaviorMetrics: BehaviorMetrics): Boolean =
        try {
            okHttpClient.newCall(
                addAuthorizationWithCheckRefreshToken(
                    createRequestBuilderWithCommonHeader(
                        getApiEndpoint("/api/plugin/b/v1/m")
                    )
                ).post(behaviorMetrics.toJsonString().toRequestBody()).build()
            ).await().let { !((it.code == 401) || (it.code == 403) || (it.code in 500..599)) }
        } catch (e: IOException) {
            false
        }

    private abstract class SenseNovaClientApi : ClientApi {
        abstract fun getRequestBodyJson(request: CodeRequest, stream: Boolean): String
        override fun addRequestBody(
            requestBuilder: Request.Builder,
            request: CodeRequest,
            stream: Boolean
        ): Request.Builder = requestBuilder.run {
            appendCommonRaccoonHeader(this, request.id, null)
            post(getRequestBodyJson(request, stream).toRequestBody())
        }

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
    }.onFailure { e ->
        if (e is CancellationException) {
            throw e
        }
    }.getOrNull()

    private suspend fun addAuthorizationWithCheckRefreshToken(requestBuilder: Request.Builder): Request.Builder =
        (checkRefreshToken()?.getPasswordAsString() ?: accessToken)?.let {
            requestBuilder.addHeader(
                "Authorization",
                "Bearer $it"
            )
        } ?: throw UnauthorizedException("access token is empty")

    override suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder =
        addAuthorizationWithCheckRefreshToken(requestBuilder)

    companion object {
        const val CLIENT_NAME = "sensecode"

        const val BASE_API = "https://raccoon-api.sensetime.com"
        const val BASE_API_DEV = "http://code-dev-api.sensetime.com"
        const val BASE_API_TEST = "http://code-test-api.sensetime.com"
        const val BASE_API_TEST_TOB = "http://raccoon-2b-test-api.sensetime.com"

        private const val API_LLM_COMPLETIONS = "/api/plugin/nova/v1/proxy/v1/llm/completions"
        private const val API_LLM_CHAT_COMPLETIONS = "/api/plugin/nova/v1/proxy/v1/llm/chat-completions"
        private const val PTC_CODE_S_MODEL_NAME = "SenseChat-CodeCompletion-Lite"
        private const val PTC_CODE_L_MODEL_NAME = "SenseChat-Code"

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

        private fun appendCommonRaccoonHeader(
            requestBuilder: Request.Builder,
            id: String?,
            action: String?
        ): Request.Builder = requestBuilder.apply {
            id?.letIfNotBlank {
                addHeader("x-raccoon-turn-id", it)
                addHeader("x-raccoon-machine-id", RaccoonUtils.machineID.ifNullOrBlank(RaccoonUtils.DEFAULT_MACHINE_ID))
            }
            action?.letIfNotBlank {
                addHeader("x-raccoon-action", it)
            }
            addHeader(
                "x-raccoon-extension",
                "${RaccoonPlugin.NAME}/${RaccoonPlugin.version} (${SystemInfo.getOsNameAndVersion()} ${SystemInfo.OS_ARCH})"
            )
            addHeader(
                "x-raccoon-ide",
                "${ApplicationInfo.getInstance().versionName}/${ApplicationInfo.getInstance().strictVersion} (${ApplicationInfo.getInstance().apiVersion})"
            )
        }
    }
}