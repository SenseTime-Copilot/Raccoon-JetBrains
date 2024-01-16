package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
val RACCOON_STATISTICS_TOPIC = Topic.create("RaccoonStatisticsTopic", RaccoonStatisticsListener::class.java)

interface RaccoonStatisticsListener {
    fun onToolWindowRequest()
    fun onToolWindowResponseCode()
    fun onToolWindowCodeAccepted()

    fun onInlineCompletionRequest()
    fun onInlineCompletionAccepted()
}