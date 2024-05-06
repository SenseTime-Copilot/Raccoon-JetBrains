package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCodeChunk
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.UserMessage

@Topic.ProjectLevel
internal val RACCOON_TASKS_TOPIC = Topic.create("RaccoonTasksTopic", RaccoonTasksListener::class.java)

internal interface RaccoonTasksListener {
    fun onNewTask(userMessage: UserMessage, localKnowledge: List<LLMCodeChunk>?)
}