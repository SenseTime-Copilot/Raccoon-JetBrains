package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.applyIf
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMAgentRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponseError
import com.sensetime.sensecode.jetbrains.raccoon.llm.models.PenroseAgentModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.llm.models.PenroseChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.llm.models.PenroseCompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.*
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.AgentModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfigJson
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonResources
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_SENSITIVE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonSensitiveListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.LoginDialog
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.UserAuthorizationPanel
import com.sensetime.sensecode.jetbrains.raccoon.utils.*
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.awt.Component
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JPanel


internal class RaccoonClient : LLMClient() {
    override val name: String = NAME
    override val clientConfig: ClientConfig
        get() = raccoonClientConfig


    // build requests

    override fun createRequestBuilderWithCommonHeader(apiEndpoint: String, stream: Boolean): Request.Builder {
        val orgCode = RaccoonUserInformation.getInstance().currentOrgCode
        val isOrg = !orgCode.isNullOrBlank()
        return super.createRequestBuilderWithCommonHeader(
            if (isOrg) RaccoonClientConfig.apiPathToOrg(apiEndpoint) else apiEndpoint, stream
        ).addHeader("x-raccoon-machine-id", RaccoonUtils.machineID)
            .addHeader("x-raccoon-extension", RaccoonPlugin.pluginInfo)
            .addHeader("x-raccoon-ide", RaccoonPlugin.ideInfo).applyIf(isOrg) {
                addHeader("x-org-code", orgCode!!)
            }
    }

    private fun Request.Builder.addAuthorizationHeaderInsideEdtAndCatching(accessToken: String): Request.Builder =
        apply { addHeader("Authorization", "Bearer $accessToken") }

    override suspend fun Request.Builder.addAuthorizationHeaderInsideEdtAndCatching(clientJobRunner: ClientJobRunner): Request.Builder =
        addAuthorizationHeaderInsideEdtAndCatching(clientJobRunner.getAccessTokenWithRefreshInsideCatching())

    override fun Request.Builder.addLLMBodyInsideEdtAndCatching(llmRequest: LLMRequest): Request.Builder =
        when (llmRequest) {
            is LLMAgentRequest -> Pair(llmRequest.id, NovaClientAgentRequest(llmRequest, clientConfig.agentModelConfig))
            is LLMChatRequest -> Pair(
                llmRequest.id,
                NovaClientChatRequest(
                    llmRequest,
                    clientConfig.chatModelConfig,
                    RaccoonUserInformation.getInstance().knowledgeBases?.knowledgeBases?.takeIf { getIsKnowledgeBaseAllowed() && RaccoonSettingsState.instance.isCloudKnowledgeBaseEnabled }
                        ?.let { knowledgeBases ->
                            mapOf("know_ids" to JsonArray(knowledgeBases.map { JsonPrimitive(it.code) }))
                        })
            )

            is LLMCompletionRequest -> Pair(
                null, NovaClientCompletionRequest(llmRequest, clientConfig.completionModelConfig)
            )
        }.let { (id, novaClientRequest) ->
            appendRaccoonRequestActionHeader(id, llmRequest.action).post(
                novaClientRequest.toJsonString().toRequestBody()
            )
        }


    // decode responses

    override fun decodeToLLMResponseError(body: String): LLMResponseError =
        decodeToLLMAgentResponseInsideEdtAndCatching(body)

    override fun decodeToLLMCompletionResponseInsideEdtAndCatching(body: String): LLMCompletionResponse =
        LLMClientJson.decodeFromString(
            RaccoonClientLLMResponse.serializer(NovaClientLLMCompletionChoice.serializer()), body
        )

    override fun decodeToLLMChatResponseInsideEdtAndCatching(body: String): LLMChatResponse =
        LLMClientJson.decodeFromString(
            RaccoonClientLLMResponse.serializer(NovaClientLLMChatChoice.serializer()), body
        )

    override fun decodeToLLMAgentResponseInsideEdtAndCatching(body: String): LLMAgentResponse =
        LLMClientJson.decodeFromString(
            RaccoonClientLLMResponse.serializer(NovaClientLLMAgentChoice.serializer()), body
        )


    // login ui

    override suspend fun onUnauthorizedInsideEdt(isEnableNotify: Boolean, project: Project?) {
        clearLoginTokens()
        project?.takeIf { isEnableNotify }?.let {
            notifyGotoLogin(it, null)
        }
    }

    override fun makeUserAuthorizationPanel(parent: Disposable): JPanel {
        val userInformation = RaccoonUserInformation.getInstance()
        return UserAuthorizationPanel(
            userInformation.getDisplayUserName(), userInformation.isProCodeEnabled(),
            userInformation.getCurrentOrgDisplayName(), userInformation.currentOrgAvailable(),
            object : UserAuthorizationPanel.EventListener {
                private var logoutJob: Job? = null
                    set(value) {
                        field?.cancel()
                        field = value
                    }
                private var getOrganizationsJob: Job? = null
                    set(value) {
                        field?.cancel()
                        field = value
                    }

                init {
                    Disposer.register(parent) {
                        logoutJob = null
                        getOrganizationsJob = null
                    }
                }

                override fun onLoginClicked(parent: Component, onFinallyInsideEdt: () -> Unit) {
                    try {
                        LoginDialog(
                            null, parent,
                            raccoonClientConfig.getWebLoginUrl(),
                            raccoonClientConfig.getWebForgotPasswordUrl()
                        ).showAndGet()
                    } finally {
                        onFinallyInsideEdt()
                    }
                }

                override fun onLogoutClicked(onFinallyInsideEdt: () -> Unit) {
                    logoutJob = LLMClientManager.launchClientJob {
                        RaccoonExceptions.resultOf({ logout(null) }, onFinallyInsideEdt)
                    }
                }

                override fun onOrganizationSelected(orgCode: String) {
                    userInformation.currentOrgCode = orgCode
                    getOrganizationsJob = LLMClientManager.launchClientJob {
                        RaccoonExceptions.resultOf {
                            runClientJob(isEnableNotify = true, true, null, null) {
                                RaccoonUserInformation.getInstance().knowledgeBases =
                                    requestKnowledgeBasesInsideCatching(getAccessTokenWithRefreshInsideCatching())
                            }
                        }
                    }
                }

                override fun getOrganizations(
                    parent: Component,
                    onFinallyInsideEdt: (t: Throwable?, isCodePro: Boolean, List<RaccoonClientOrgInfo>?) -> Unit
                ) {
                    getOrganizationsJob = LLMClientManager.launchClientJob {
                        var t: Exception? = null
                        var result: RaccoonClientUserInfo? = null
                        try {
                            result = withTimeout(5 * 1000L) {
                                requestUserInfo(
                                    isEnableNotify = true,
                                    isEnableDebugLog = true,
                                    null,
                                    parent
                                )
                            }
                        } catch (e: Exception) {
                            t = e
                            e.throwIfMustRethrow()
                        } finally {
                            onFinallyInsideEdt(t, (true == result?.proCodeEnabled), result?.organizations)
                        }
                    }
                }
            })
    }


    // auth request

    private suspend fun ClientJobRunner.requestKnowledgeBasesInsideCatching(accessToken: String): RaccoonClientKnowledgeBases =
        LLMClientJson.decodeFromString(
            RaccoonClientKnowledgeBasesResponse.serializer(), requestInsideEdtAndCatching(
                createRequestBuilderWithCommonHeader(
                    raccoonClientConfig.getKnowledgeBasesApiEndpoint(), false
                ).addAuthorizationHeaderInsideEdtAndCatching(accessToken).get().build()
            )
        ).let { knowledgeBasesResponse ->
            // todo set knowledgeBases null if failed
            knowledgeBasesResponse.throwIfError()
            requireNotNull(knowledgeBasesResponse.data) { "not found data field in ${knowledgeBasesResponse::class::simpleName}" }
        }

    private suspend fun ClientJobRunner.updateTokensResponseBodyInsideCatching(body: String): String =
        LLMClientJson.decodeFromString(RaccoonClientTokensResponse.serializer(), body).let { tokensResponse ->
            tokensResponse.throwIfError()
            requireNotNull(tokensResponse.data) { "not found data field in ${tokensResponse::class::simpleName}" }.let { tokensResponseData ->
                requestUserInfoInsideCatching(tokensResponseData.accessToken).let { userInfo ->
                    RaccoonUserInformation.getInstance().updateAuthorizationTokens(
                        tokensResponseData.accessToken,
                        tokensResponseData.refreshToken,
                        userInfo
                    )
                }
                RaccoonUserInformation.getInstance().knowledgeBases =
                    requestKnowledgeBasesInsideCatching(tokensResponseData.accessToken)
                tokensResponseData.accessToken
            }
        }

    private suspend fun ClientJobRunner.getAccessTokenWithRefreshInsideCatching(): String =
        RaccoonUserInformation.getInstance().getAuthorizationTokens().let { (accessToken, refreshTokenIfExpired) ->
            RaccoonExceptions.resultOf {
                // ignore refreshToken exceptions, fallback to accessToken
                refreshTokenIfExpired?.let { refreshToken ->
                    updateTokensResponseBodyInsideCatching(
                        requestInsideEdtAndCatching(
                            createRequestBuilderWithCommonHeader(
                                raccoonClientConfig.getRefreshTokenPathApiEndpoint(),
                                false
                            ).post(
                                LLMClientJson.encodeToString(
                                    RaccoonClientRefreshTokenRequest.serializer(),
                                    RaccoonClientRefreshTokenRequest(refreshToken)
                                ).toRequestBody()
                            ).build()
                        )
                    )
                }
            }.getOrNull() ?: accessToken
        } ?: throw LLMClientUnauthorizedException("access token is empty")

    private suspend fun ClientJobRunner.requestUserInfoInsideCatching(accessToken: String): RaccoonClientUserInfo =
        LLMClientJson.decodeFromString(
            RaccoonClientUserInfoResponse.serializer(), requestInsideEdtAndCatching(
                createRequestBuilderWithCommonHeader(
                    raccoonClientConfig.getUserInfoPathApiEndpoint(),
                    false
                ).addAuthorizationHeaderInsideEdtAndCatching(accessToken).get().build()
            )
        ).let { userInfoResponse ->
            userInfoResponse.throwIfError()
            requireNotNull(userInfoResponse.data) { "not found data field in ${userInfoResponse::class::simpleName}" }
        }

    suspend fun loginWithPhone(
        project: Project?, uiComponentForEdt: Component?,
        nationCode: String, rawPhoneNumber: String, rawPassword: CharArray
    ): String = createRequestBuilderWithCommonHeader(raccoonClientConfig.getLoginWithPhoneApiEndpoint(), false).post(
        LLMClientJson.encodeToString(
            RaccoonClientLoginWithPhoneBody.serializer(),
            RaccoonClientLoginWithPhoneBody(nationCode, rawPhoneNumber, rawPassword)
        ).toRequestBody()
    ).build()
        .runRequestJob(
            isEnableNotify = false,
            isEnableDebugLog = false,
            project,
            uiComponentForEdt
        ) { tokensResponseBody ->
            updateTokensResponseBodyInsideCatching(tokensResponseBody)
        }

    suspend fun loginWithEmail(
        project: Project?, uiComponentForEdt: Component?, email: String, rawPassword: CharArray
    ): String = createRequestBuilderWithCommonHeader(raccoonClientConfig.getLoginWithEmailApiEndpoint(), false).post(
        LLMClientJson.encodeToString(
            RaccoonClientLoginWithEmailBody.serializer(),
            RaccoonClientLoginWithEmailBody(email, rawPassword)
        ).toRequestBody()
    ).build()
        .runRequestJob(
            isEnableNotify = false,
            isEnableDebugLog = false,
            project,
            uiComponentForEdt
        ) { tokensResponseBody ->
            updateTokensResponseBodyInsideCatching(tokensResponseBody)
        }

    private suspend fun logout(project: Project?) {
        RaccoonExceptions.resultOf({
            withTimeout(5 * 1000L) {
                runClientJob(isEnableNotify = false, isEnableDebugLog = false, project, null) {
                    requestInsideEdtAndCatching(
                        createRequestBuilderWithCommonHeaderAndAuthorization(
                            raccoonClientConfig.getLogoutPathApiEndpoint(), false
                        ).post(EMPTY_POST_REQUEST_BODY).build()
                    )
                }
            }
        }, ::clearLoginTokens)
    }

    suspend fun requestUserInfo(
        isEnableNotify: Boolean,
        isEnableDebugLog: Boolean,
        project: Project?,
        uiComponentForEdt: Component?
    ): RaccoonClientUserInfo = runClientJob(isEnableNotify, isEnableDebugLog, project, uiComponentForEdt) {
        val accessToken = getAccessTokenWithRefreshInsideCatching()
        requestUserInfoInsideCatching(accessToken).also {
            RaccoonUserInformation.getInstance().updateUserInfo(it)
            RaccoonUserInformation.getInstance().knowledgeBases = requestKnowledgeBasesInsideCatching(accessToken)
        }
    }


    // behaviorMetrics
    suspend fun uploadBehaviorMetrics(behaviorMetrics: RaccoonClientBehaviorMetrics): Boolean =
        try {
            runClientJob(isEnableNotify = false, isEnableDebugLog = false, null, null) {
                requestInsideEdtAndCatching(
                    createRequestBuilderWithCommonHeaderAndAuthorization(
                        raccoonClientConfig.getBehaviorMetricsApiEndpoint(), false
                    ).post(behaviorMetrics.toJsonString().toRequestBody()).build()
                )
            }
            true
        } catch (e: LLMClientException) {
            (e as? LLMClientResponseException)?.response?.code?.let {
                !((it == 401) || (it == 403) || (it in 500..599))
            } ?: true
        } catch (e: IOException) {
            false
        }


    // sensitive
    private var lastSensitiveTimeS = AtomicLong(RaccoonUtils.getDateTimestampS())
    override suspend fun onSuccessfulHeaderInsideEdtAndCatching(headers: Headers) {
        RaccoonExceptions.resultOf {
            headers.values("x-raccoon-sensetive").firstOrNull()?.toLongOrNull()?.let { currentSensitiveTimeS ->
                val startTimeS = lastSensitiveTimeS.get()
                if (currentSensitiveTimeS > startTimeS) {
                    val tmpTimeS = RaccoonUtils.getDateTimestampS()
                    getSensitiveConversations(startTimeS.toString(), action = "response header").let { sensitives ->
                        lastSensitiveTimeS.set(tmpTimeS)
                        if (sensitives.isNotEmpty()) {
                            ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_SENSITIVE_TOPIC)
                                .onNewSensitiveConversations(sensitives)
                        }
                    }
                }
            }
        }
    }

    override suspend fun getSensitiveConversations(
        startTime: String, endTime: String?, action: String
    ): Map<String, RaccoonSensitiveListener.SensitiveConversation> = RaccoonExceptions.resultOf {
        runClientJob(isEnableNotify = false, isEnableDebugLog = false, null, null) {
            LLMClientJson.decodeFromString(
                RaccoonClientSensitivesResponse.serializer(), requestInsideEdtAndCatching(
                    createRequestBuilderWithCommonHeaderAndAuthorization(
                        buildSensitiveGetUrl(startTime, endTime), false
                    ).appendRaccoonRequestActionHeader(null, action).get().build()
                )
            ).let { sensitivesResponse ->
                sensitivesResponse.throwIfError()
                requireNotNull(sensitivesResponse.data) { "not found data field in ${sensitivesResponse::class::simpleName}" }.list.toSensitiveConversationMap()
            }
        }
    }.getOrNull() ?: super.getSensitiveConversations(startTime, endTime, action)


    // configs

    @Serializable
    private data class RaccoonCompletionApiConfig(
        override val path: String = RaccoonClientConfig.getPluginApiPath("/llm/v1/completions"),
        override val models: List<PenroseCompletionModelConfig> = listOf(PenroseCompletionModelConfig())
    ) : ClientConfig.ClientApiConfig<CompletionModelConfig>()

    @Serializable
    private data class RaccoonChatApiConfig(
        override val path: String = RaccoonClientConfig.getPluginApiPath("/llm/v1/chat-completions"),
        override val models: List<PenroseChatModelConfig> = listOf(PenroseChatModelConfig())
    ) : ClientConfig.ClientApiConfig<ChatModelConfig>()

    @Serializable
    private data class RaccoonAgentApiConfig(
        override val path: String = RaccoonClientConfig.getPluginApiPath("/llm/v1/chat-completions"),
        override val models: List<PenroseAgentModelConfig> = listOf(PenroseAgentModelConfig())
    ) : ClientConfig.ClientApiConfig<AgentModelConfig>()

    @Serializable
    private data class RaccoonClientConfig(
        override val apiBaseUrl: String = "https://raccoon-api.sensetime.com"
    ) : ClientConfig {
        @Transient
        override val name: String = NAME
        override val completionApiConfig: RaccoonCompletionApiConfig = RaccoonCompletionApiConfig()
        override val chatApiConfig: RaccoonChatApiConfig = RaccoonChatApiConfig()
        override val agentApiConfig: RaccoonAgentApiConfig = RaccoonAgentApiConfig()

        private val webBaseUrl: String = Regex("-?api-?").replace(apiBaseUrl, "")
        private val webLoginPath: String = "/login"
        fun getWebLoginUrl(): String = webBaseUrl + webLoginPath
        private val webForgotPasswordPath = "$webLoginPath?step=forgot-password"
        fun getWebForgotPasswordUrl(): String = webBaseUrl + webForgotPasswordPath

        private val loginWithPhonePath: String = getPluginApiPath("/auth/v1/login_with_password")
        fun getLoginWithPhoneApiEndpoint(): String = getApiEndpoint(loginWithPhonePath)

        private val loginWithEmailPath: String = getPluginApiPath("/auth/v1/login_with_email_password")
        fun getLoginWithEmailApiEndpoint(): String = getApiEndpoint(loginWithEmailPath)

        private val logoutPath: String = getPluginApiPath("/auth/v1/logout")
        fun getLogoutPathApiEndpoint(): String = getApiEndpoint(logoutPath)

        private val refreshTokenPath: String = getPluginApiPath("/auth/v1/refresh")
        fun getRefreshTokenPathApiEndpoint(): String = getApiEndpoint(refreshTokenPath)

        private val userInfoPath: String = getPluginApiPath("/auth/v1/user_info")
        fun getUserInfoPathApiEndpoint(): String = getApiEndpoint(userInfoPath)

        private val knowledgeBasesPath: String = getPluginApiPath("/knowledge_base/v1/knowledge_bases")
        fun getKnowledgeBasesApiEndpoint(): String = getApiEndpoint(knowledgeBasesPath)

        private val behaviorMetricsPath: String = getPluginApiPath("/b/v1/m")
        fun getBehaviorMetricsApiEndpoint(): String = getApiEndpoint(behaviorMetricsPath)

        private val sensitivePath: String = getPluginApiPath("/sensetive/v1/sensetives")
        fun getSensitiveApiEndpoint(): String = getApiEndpoint(sensitivePath)

        companion object {
            private const val PLUGIN_API_BASE_PATH: String = "/api/plugin"
            fun apiPathToOrg(srcPath: String): String {
                val startIndex = srcPath.indexOf(PLUGIN_API_BASE_PATH)
                require(startIndex >= 0) { "Not found plugin api path $PLUGIN_API_BASE_PATH in $srcPath" }
                val endIndex = startIndex + PLUGIN_API_BASE_PATH.length
                if (srcPath.indexOf("/auth", endIndex) == endIndex) {
                    return srcPath
                }
                return srcPath.substring(0, endIndex) + "/org" + srcPath.substring(endIndex)
            }

            fun getPluginApiPath(
                pluginApiSubPath: String
            ): String = PLUGIN_API_BASE_PATH + pluginApiSubPath

            @JvmStatic
            fun loadFromJsonString(jsonString: String): RaccoonClientConfig = RaccoonConfigJson.decodeFromString(
                serializer(), jsonString.also { LOG.debug { "Load $NAME config from json $it" } })
                .also {
                    LOG.trace { "Load $NAME config ok, result ${RaccoonConfigJson.encodeToString(serializer(), it)}" }
                }

            @JvmStatic
            fun loadFromResources(): RaccoonClientConfig =
                loadFromJsonString(requireNotNull(RaccoonResources.getResourceContent("${RaccoonResources.CONFIGS_DIR}/$NAME.json"))).also {
                    require(NAME == it.name) { "Client name: expected ($NAME) != actual (${it.name})" }
                }
        }
    }


    companion object {
        private val LOG = logger<RaccoonClient>()
        private val NAME: String = RaccoonClient::class.simpleName!!

        private val raccoonClientConfig: RaccoonClientConfig = RaccoonClientConfig.loadFromResources()
        val clientConfig: ClientConfig
            get() = raccoonClientConfig

        @JvmStatic
        fun getIsKnowledgeBaseAllowed(): Boolean = RaccoonUserInformation.getInstance().IsKnowledgeBaseAllowed()

        @JvmStatic
        fun notifyGotoLogin(project: Project, parent: Component?) {
            RaccoonNotification.notificationGroup.createNotification(
                RaccoonBundle.message("notification.settings.login.notloggedin"), "", NotificationType.WARNING
            ).addAction(NotificationAction.createSimple(RaccoonBundle.message("notification.settings.goto.login")) {
                LoginDialog(
                    project, parent,
                    raccoonClientConfig.getWebLoginUrl(),
                    raccoonClientConfig.getWebForgotPasswordUrl()
                ).showAndGet()
            }).notify(project)
        }

        @JvmStatic
        private fun clearLoginTokens() {
            RaccoonUserInformation.getInstance().restore()
            ApplicationManager.getApplication().messageBus.syncPublisher(
                RACCOON_CLIENT_AUTHORIZATION_TOPIC
            ).onCurrentOrganizationNameChanged(
                null, false, false
            )
            ApplicationManager.getApplication().messageBus.syncPublisher(
                RACCOON_CLIENT_AUTHORIZATION_TOPIC
            ).onUserNameChanged(null)
        }

        @JvmStatic
        private fun buildSensitiveGetUrl(
            startTime: String, endTime: String?
        ): String = raccoonClientConfig.getSensitiveApiEndpoint().toHttpUrl().newBuilder().run {
            addQueryParameter("start_time", startTime)
            endTime?.letIfNotBlank { addQueryParameter("end_time", it) }
            addQueryParameter("machine_id", RaccoonUtils.machineID)
            build().toString()
        }

        private fun Request.Builder.appendRaccoonRequestActionHeader(
            id: String?, action: String?
        ): Request.Builder = apply {
            id?.letIfNotBlank { addHeader("x-raccoon-turn-id", it) }
            action?.letIfNotBlank { addHeader("x-raccoon-action", it) }
        }
    }
}