package com.sensetime.intellij.plugins.sensecode.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC =
    Topic.create("SenseCodeClientAuthorizationTopic", SenseCodeClientAuthorizationListener::class.java)

interface SenseCodeClientAuthorizationListener {
    fun onUserNameChanged(userName: String?) {}

    fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {}
}