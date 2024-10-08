package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic


@Topic.ProjectLevel
internal val RACCOON_REQUEST_STATE_TOPIC =
    Topic.create("RaccoonRequestStateTopic", RaccoonRequestStateListener::class.java)

internal interface RaccoonRequestStateListener {
    fun onStartInsideEdtAndCatching(id: Long, action: String?) {}
    fun onDoneInsideEdtAndCatching(id: Long, message: String?) {}
    fun onFailureIncludeCancellationInsideEdtAndCatching(id: Long, t: Throwable) {}
    fun onFinallyInsideEdtAndCatching(id: Long) {}
}
