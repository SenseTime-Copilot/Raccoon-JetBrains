package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC =
    Topic.create("RaccoonClientAuthorizationTopic", RaccoonClientAuthorizationListener::class.java)

interface RaccoonClientAuthorizationListener {
    fun onUserNameChanged(userName: String?) {}

    fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {}
}