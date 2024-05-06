package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
internal val RACCOON_CLIENT_AUTHORIZATION_TOPIC =
    Topic.create("RaccoonClientAuthorizationTopic", RaccoonClientAuthorizationListener::class.java)

internal interface RaccoonClientAuthorizationListener {
    fun onUserNameChanged(userName: String?) {}
    fun onCurrentOrganizationNameChanged(orgName: String?, isAvailable: Boolean, isCodePro: Boolean) {}
}
