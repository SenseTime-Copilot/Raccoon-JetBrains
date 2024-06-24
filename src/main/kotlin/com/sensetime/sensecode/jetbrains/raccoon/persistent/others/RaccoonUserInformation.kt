package com.sensetime.sensecode.jetbrains.raccoon.persistent.others

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientMessageException
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientKnowledgeBases
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientOrgInfo
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientUserInfo
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonPersistentJson
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonPersistentStateComponent
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils.EMPTY_JSON_OBJECT_STRING
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils.isExpiredS
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.serialization.Serializable
import java.util.*


@Service
@State(
    name = "com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation",
    storages = [Storage("RaccoonJetBrainsOthers.xml")]
)
internal class RaccoonUserInformation : RaccoonPersistentStateComponent<RaccoonUserInformation.State>(State()) {
    class State : RaccoonPersistentStateComponent.State() {
        var accessToken by string()
        var refreshToken by string()
        var userNameInAccessToken by string()
        var accessTokenExp by property(-1L)

        var currentOrgCode by string()
        var userInfoJsonString by string(EMPTY_JSON_OBJECT_STRING)

        var knowledgeBasesJsonString by string(EMPTY_JSON_OBJECT_STRING)
    }

    @Serializable
    private data class AccessTokenJWTPayload(val name: String, val exp: Long)


    fun getAuthorizationTokens(): Pair<String?, String?> = Pair(
        state.accessToken,
        state.refreshToken.takeIf { (state.accessTokenExp > 0) && (state.accessTokenExp.isExpiredS(15L)) })

    var currentOrgCode: String?
        get() = state.currentOrgCode
        set(value) {
            state.currentOrgCode = value
            getCurrentOrgInfoOrNull().let { currentOrgInfo ->
                ApplicationManager.getApplication().messageBus.syncPublisher(
                    RACCOON_CLIENT_AUTHORIZATION_TOPIC
                ).onCurrentOrganizationNameChanged(
                    currentOrgInfo?.getDisplayOrgName(), currentOrgInfo.isAvailable(), isProCodeEnabled()
                )
            }
        }

    private var userInfo: RaccoonClientUserInfo?
        get() = RaccoonExceptions.resultOf {
            state.userInfoJsonString?.letIfNotBlank {
                RaccoonPersistentJson.decodeFromString(RaccoonClientUserInfo.serializer(), it)
            }
        }.getOrNull()
        set(value) {
            state.userInfoJsonString = value?.let {
                RaccoonPersistentJson.encodeToString(RaccoonClientUserInfo.serializer(), it)
            }
        }

    var knowledgeBases: RaccoonClientKnowledgeBases?
        get() = RaccoonExceptions.resultOf {
            state.knowledgeBasesJsonString?.letIfNotBlank {
                RaccoonPersistentJson.decodeFromString(RaccoonClientKnowledgeBases.serializer(), it)
            }
        }.getOrNull()
        set(value) {
            state.knowledgeBasesJsonString = value?.let {
                RaccoonPersistentJson.encodeToString(RaccoonClientKnowledgeBases.serializer(), it)
            }
        }

    fun updateUserInfo(newUserInfo: RaccoonClientUserInfo?) {
        userInfo = newUserInfo
        ApplicationManager.getApplication().messageBus.syncPublisher(
            RACCOON_CLIENT_AUTHORIZATION_TOPIC
        ).onUserNameChanged(newUserInfo.getDisplayUserName())
        if (currentOrgCode.isNullOrBlank()) {
            ApplicationManager.getApplication().messageBus.syncPublisher(
                RACCOON_CLIENT_AUTHORIZATION_TOPIC
            ).onCurrentOrganizationNameChanged("", false, newUserInfo.isProCodeEnabled())
        }
    }

    fun updateAuthorizationTokens(
        accessToken: String,
        refreshToken: String?,
        newUserInfo: RaccoonClientUserInfo?,
        isCheckOrg: Boolean
    ) {
        var tmpOrgCode: String? = null
        if (isCheckOrg && RaccoonConfig.config.isToB()) {
            tmpOrgCode = newUserInfo?.getFirstAvailableOrgInfoOrNull()?.code
            if (tmpOrgCode.isNullOrBlank()) {
                throw LLMClientMessageException(RaccoonBundle.message("authorization.panel.organizations.list.empty"))
            }
        }

        state.accessToken = accessToken
        state.refreshToken = refreshToken
        RaccoonExceptions.resultOf {
            RaccoonPersistentJson.decodeFromString(
                AccessTokenJWTPayload.serializer(),
                Base64.getUrlDecoder().decode(accessToken.split(".")[1]).decodeToString()
            )
        }.onSuccess {
            state.accessTokenExp = it.exp
            state.userNameInAccessToken = it.name
        }
        updateUserInfo(newUserInfo)
        if (null != tmpOrgCode) {
            currentOrgCode = tmpOrgCode
        } else if (null == newUserInfo?.getOrgInfoByCodeOrNull(currentOrgCode)) {
            currentOrgCode = ""
        }
    }


    fun isProCodeEnabled(): Boolean = userInfo.isProCodeEnabled()
    fun getDisplayUserName(): String? = userInfo.getDisplayUserName()
    private fun RaccoonClientUserInfo?.getDisplayUserName(): String? =
        this?.getDisplayUserName(currentOrgCode) ?: state.userNameInAccessToken

    fun currentOrgAvailable(): Boolean = getCurrentOrgInfoOrNull().isAvailable()
    fun getCurrentOrgDisplayName(): String? = getCurrentOrgInfoOrNull()?.getDisplayOrgName()
    private fun getCurrentOrgInfoOrNull(): RaccoonClientOrgInfo? =
        userInfo?.getOrgInfoByCodeOrNull(currentOrgCode)

    fun IsKnowledgeBaseAllowed(): Boolean = RaccoonSettingsState.instance.isKnowledgeEnabled && (isProCodeEnabled() || currentOrgAvailable())


    companion object {
        fun getInstance(): RaccoonUserInformation = service()

        private fun RaccoonClientUserInfo?.isProCodeEnabled(): Boolean = (true == this?.proCodeEnabled)
        private fun RaccoonClientOrgInfo?.isAvailable(): Boolean = (true == this?.isAvailable())
    }
}
