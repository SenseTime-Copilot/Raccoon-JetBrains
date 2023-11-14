package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.Usage

@Topic.AppLevel
val SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC =
    Topic.create("RaccoonClientRequestStateTopic", RaccoonClientRequestStateListener::class.java)

interface RaccoonClientRequestStateListener {
    fun onStart(id: Long) {}
    fun onDone(id: Long, usage: Usage?) {}
    fun onError(id: Long, error: String?) {}
    fun onFinally(id: Long) {}
}