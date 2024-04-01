package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic


@Topic.AppLevel
internal val RACCOON_SENSITIVE_TOPIC = Topic.create("RaccoonSensitiveTopic", RaccoonSensitiveListener::class.java)

internal interface RaccoonSensitiveListener {
    interface SensitiveConversation {
        val type: String?
    }

    fun onNewSensitiveConversations(sensitiveConversations: Map<String, SensitiveConversation>)
}