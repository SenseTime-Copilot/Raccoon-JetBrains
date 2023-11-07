package com.sensetime.intellij.plugins.sensecode.topics

import com.intellij.util.messages.Topic
import com.sensetime.intellij.plugins.sensecode.clients.responses.Usage

@Topic.AppLevel
val SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC =
    Topic.create("SenseCodeClientRequestStateTopic", SenseCodeClientRequestStateListener::class.java)

interface SenseCodeClientRequestStateListener {
    fun onStart(id: Long) {}
    fun onDone(id: Long, usage: Usage?) {}
    fun onError(id: Long, error: String?) {}
    fun onFinally(id: Long) {}
}