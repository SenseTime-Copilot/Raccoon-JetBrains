package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.AppLevel

@AppLevel
val SENSE_CODE_CREDENTIALS_TOPIC = Topic.create("SenseCodeCredentialsTopic", SenseCodeCredentialsListener::class.java)

interface SenseCodeCredentialsListener {
    fun onClientAuthChanged(name: String, key: String)
}