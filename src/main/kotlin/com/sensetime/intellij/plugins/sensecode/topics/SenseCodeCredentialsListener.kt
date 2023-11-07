package com.sensetime.intellij.plugins.sensecode.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_CREDENTIALS_TOPIC = Topic.create("SenseCodeCredentialsTopic", SenseCodeCredentialsListener::class.java)

interface SenseCodeCredentialsListener {
    fun onClientAuthChanged(name: String, key: String)
}