package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
internal val RACCOON_CREDENTIALS_TOPIC = Topic.create("RaccoonCredentialsTopic", RaccoonCredentialsListener::class.java)

internal interface RaccoonCredentialsListener {
    fun onClientAuthChanged(name: String, key: String)
}
