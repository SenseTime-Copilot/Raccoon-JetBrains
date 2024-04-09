package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientOrgInfo
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientUserInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal data class RaccoonClientUserInfoSettings(
    @SerialName("current_org_code")
    private val _currentOrgCode: String? = null,
    @SerialName("user_info")
    val userInfo: RaccoonClientUserInfo? = null
) {
    val currentOrgCode: String?
        get() = _currentOrgCode.takeIf { RaccoonConfig.config.isTeam() }

    val proCodeEnabled: Boolean = (true == userInfo?.proCodeEnabled)

    fun getDisplayUserName(): String? = userInfo?.getDisplayUserName(currentOrgCode)

    fun currentOrgAvailable(): Boolean = (true == getCurrentOrgInfoOrNull()?.isAvailable())
    fun getCurrentOrgDisplayName(): String? = getCurrentOrgInfoOrNull()?.getDisplayOrgName()
    fun getCurrentOrgInfoOrNull(): RaccoonClientOrgInfo? = currentOrgCode?.let { userInfo?.getOrgInfoByCodeOrNull(it) }
}
