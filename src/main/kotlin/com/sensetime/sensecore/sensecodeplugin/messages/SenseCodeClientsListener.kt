package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_CLIENTS_TOPIC = Topic.create("SenseCodeClientsTopic", SenseCodeClientsListener::class.java)

interface SenseCodeClientsListener {
    fun onUserNameChanged(userName: String?) {}

    fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {}
}