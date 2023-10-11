package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.util.messages.Topic
import com.sensetime.sensecore.sensecodeplugin.clients.responses.Usage

@Topic.AppLevel
val SENSE_CODE_CLIENTS_TOPIC = Topic.create("SenseCodeClientsTopic", SenseCodeClientsListener::class.java)

interface SenseCodeClientsListener {
    fun onUserNameChanged(userName: String?) {}

    fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {}

    fun onStart() {}
    fun onDone(usage: Usage?) {}
    fun onError(error: String?) {}
    fun onFinally() {}
}