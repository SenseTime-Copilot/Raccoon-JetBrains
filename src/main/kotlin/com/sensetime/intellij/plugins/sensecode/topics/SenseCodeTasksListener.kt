package com.sensetime.intellij.plugins.sensecode.topics

import com.intellij.util.messages.Topic
import com.sensetime.intellij.plugins.sensecode.persistent.histories.UserMessage

@Topic.AppLevel
val SENSE_CODE_TASKS_TOPIC = Topic.create("SenseCodeTasksTopic", SenseCodeTasksListener::class.java)

interface SenseCodeTasksListener {
    fun onNewTask(userMessage: UserMessage)
}