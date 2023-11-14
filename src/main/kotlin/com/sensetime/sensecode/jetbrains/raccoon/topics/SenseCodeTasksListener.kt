package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.UserMessage

@Topic.AppLevel
val SENSE_CODE_TASKS_TOPIC = Topic.create("RaccoonTasksTopic", RaccoonTasksListener::class.java)

interface RaccoonTasksListener {
    fun onNewTask(userMessage: UserMessage)
}