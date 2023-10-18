package com.sensetime.sensecore.sensecodeplugin.clients

import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.CodeResponse
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.letIfFilled
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import com.sensetime.sensecore.sensecodeplugin.clients.models.PenroseModels
import com.sensetime.sensecore.sensecodeplugin.clients.requests.AMSCodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.AMSCodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.AMSError
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SenseCoreClient : AkSkCodeClient() {
    override val name: String = CLIENT_NAME

    override fun getAkSkSettings(): AkSkSettings = AkSkSettings(
        "$name ak/sk",
        "SenseCore AccessKey ID and Secret: ${
            SenseCodeBundle.message(
                "settings.group.aksk.sensecore.comment",
                "<a href='https://console.sensecore.cn/iam/Security/access-key'>sensecore access-key</a>"
            )
        }",
        AkSkSettingsItem("AccessKey ID", null, this::akGetter, this::akSetter),
        AkSkSettingsItem("AccessKey Secret", null, this::skGetter, this::skSetter)
    )

    override suspend fun addAuthorization(
        requestBuilder: Request.Builder,
        apiEndpoint: String,
        utcDate: String
    ): Request.Builder =
        aksk.letIfFilled { ak, sk ->
            val message = "date: ${utcDate}\nPOST /studio/ams/data/v1/chat/completions HTTP/1.1"

            val sha256HMAC = Mac.getInstance("HmacSHA256")
            sha256HMAC.init(SecretKeySpec(sk.toByteArray(), "HmacSHA256"))
            val signature = Base64.getEncoder().encodeToString(sha256HMAC.doFinal(message.toByteArray()))

            requestBuilder.addHeader(
                "Authorization",
                """hmac accesskey="$ak", algorithm="hmac-sha256", headers="date request-line", signature="$signature""""
            )
        } ?: throw UnauthorizedException(name, "access token is empty")

    override fun addPostBody(
        requestBuilder: Request.Builder, request: CodeRequest, stream: Boolean
    ): Request.Builder {
        val amsRequest = AMSCodeRequest.makeAMSCodeRequest(request, stream)
        val amsRequestJson = SenseCodeClientJson.encodeToString(AMSCodeRequest.serializer(), amsRequest)
        return requestBuilder.post(amsRequestJson.toRequestBody())
    }

    override fun toCodeResponse(body: String, stream: Boolean): CodeResponse = kotlin.runCatching {
        SenseCodeClientJson.decodeFromString(AMSError.serializer(), body).takeIf { it.hasError() }?.let {
            AMSCodeResponse(error = it)
        }
    }.getOrNull() ?: SenseCodeClientJson.decodeFromString(AMSCodeResponse.serializer(), body)


    companion object {
        const val CLIENT_NAME = "sensecode"
        const val API_ENDPOINT = "https://ams.sensecoreapi.cn/studio/ams/data/v1/chat/completions"

        private val SYSTEM_PROMPT_TEMPLATE = ModelConfig.DisplayText("")
        private const val PENROSE_MODEL_S = "penrose-411"
        private const val PENROSE_MODEL_L = "penrose-l"

        @JvmStatic
        fun getDefaultClientConfig(): ClientConfig = ClientConfig(
            CLIENT_NAME, ::SenseCoreClient, API_ENDPOINT,
            mapOf(
                PENROSE_MODEL_S to PenroseModels.makeModelSConfig(PENROSE_MODEL_S, SYSTEM_PROMPT_TEMPLATE),
                PENROSE_MODEL_L to PenroseModels.makeModelLConfig(PENROSE_MODEL_L, SYSTEM_PROMPT_TEMPLATE)
            ),
            mapOf(ClientConfig.FREE_CHAT to PENROSE_MODEL_L), PENROSE_MODEL_S
        )
    }
}
