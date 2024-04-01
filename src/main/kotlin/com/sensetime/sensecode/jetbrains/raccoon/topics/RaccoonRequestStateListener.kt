package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientException
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMUsage


@Topic.ProjectLevel
internal val RACCOON_REQUEST_STATE_TOPIC =
    Topic.create("RaccoonRequestStateTopic", RaccoonRequestStateListener::class.java)

internal interface RaccoonRequestStateListener {
    fun onStartInsideEdtAndCatching(id: Long, action: String?) {}
    fun onDoneInsideEdtAndCatching(id: Long, finishReason: String?, usage: LLMUsage?) {}
    fun onFailureInsideEdt(id: Long, llmClientException: LLMClientException) {}
    fun onFinallyInsideEdt(id: Long) {}
}
