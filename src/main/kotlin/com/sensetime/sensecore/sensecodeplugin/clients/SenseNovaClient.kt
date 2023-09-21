package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.credentialStore.Credentials
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.requests.SenseNovaCodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.CodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.SenseNovaCodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.SenseNovaStatus
import com.sensetime.sensecore.sensecodeplugin.services.http.authentication.SenseNovaAuthService
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
        @Serializable
        private data class JWTPayload(val email: String? = null, val exp: Int? = null)

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

        private const val CLIENT_NAME = "sensenova"
        private const val AKSK_KEY = "aksk"
        private const val REFRESH_TOKEN_KEY = "refreshToken"
        private const val ACCESS_TOKEN_KEY = "accessToken"
        private const val TOKEN_EXPIRES_AFTER = 3600 * 24 * 7

        private val aksk: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, AKSK_KEY)
        private val refreshToken: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, REFRESH_TOKEN_KEY)
        private val accessToken: Credentials?
            get() = SenseCodeCredentialsManager.getClientAuth(CLIENT_NAME, ACCESS_TOKEN_KEY)

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

        private fun getLoginUrl(env: String): String = "https://login.sensenova.$env/#/login"
        private fun getLogoutUrl(env: String): String = "https://iam-login.sensenova.$env/sensenova-sso/v1/logout"
        private fun getRefreshTokenUrl(env: String): String = "https://iam-login.sensenova.$env/sensenova-sso/v1/token"
    }
}