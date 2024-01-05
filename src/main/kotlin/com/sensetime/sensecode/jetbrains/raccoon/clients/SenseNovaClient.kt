package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sensetime.sensecode.jetbrains.raccoon.clients.models.PenroseModels
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMChatCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.SenseNovaLLMCompletionsRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toClientApiConfigMap
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.toModelConfigMap
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*

class SenseNovaClient : CodeClient() {
    override val name: String
        get() = CLIENT_NAME

    override val userName: String
        get() = "ak/sk user"

//    private fun akGetter() = RaccoonCredentialsManager.getClientAk(name) ?: ""
//    private fun akSetter(ak: String) {
//        RaccoonCredentialsManager.setClientAk(name, ak)
//    }
//
//    private fun skGetter() = RaccoonCredentialsManager.getClientSk(name) ?: ""
//    private fun skSetter(sk: String) {
//        RaccoonCredentialsManager.setClientSk(name, sk)
//    }
//
//    override fun getAkSkSettings(): AkSkSettings = AkSkSettings(
//        "$name ak/sk",
//        "SenseNova AccessKey ID and Secret: ${
//            RaccoonBundle.message(
//                "settings.group.aksk.nova.comment",
//                "<a href='https://console.sensenova.cn/#/account/access-control/access-control-home'>sensenova access control</a>"
//            )
//        }",
//        AkSkSettingsItem("Access Key ID", null, this::akGetter, this::akSetter),
//        AkSkSettingsItem("Secret Access Key", null, this::skGetter, this::skSetter)
//    )

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

    override suspend fun addAuthorization(requestBuilder: Request.Builder, utcDate: String): Request.Builder =
        getAuthorizationFromAkSk()?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
            ?: throw UnauthorizedException("access token is empty")

    private fun getAuthorizationFromAkSk(): String? =
        JWT.create().withIssuer("2YZYmICm36ywQwPRchFtNHBwiV2").withHeader(mapOf("alg" to "HS256"))
            .withExpiresAt(Date(System.currentTimeMillis() + 1800 * 1000))
            .withNotBefore(Date(System.currentTimeMillis() - 300 * 1000))
            .sign(Algorithm.HMAC256("kHvNtgwANPb3poKO8H2fbpHA6zMESKu0"))

    companion object {
        const val CLIENT_NAME = "sensenova"
        const val BASE_API = "http://172.192.194.11:8080"
        private const val API_LLM_COMPLETIONS = "/v1/llm/completions"
        private const val API_LLM_CHAT_COMPLETIONS = "/v1/llm/chat-completions"
        private const val PTC_CODE_S_MODEL_NAME = "nova-ptc-s-v1-codecompletion"
        private const val PTC_CODE_L_MODEL_NAME = "nova-ptc-l-v1-code"

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
