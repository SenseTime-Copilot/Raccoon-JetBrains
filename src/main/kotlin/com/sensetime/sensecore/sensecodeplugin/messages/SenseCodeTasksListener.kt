package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.util.messages.Topic
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation

@Topic.AppLevel
val SENSE_CODE_TASKS_TOPIC = Topic.create("SenseCodeTasksTopic", SenseCodeTasksListener::class.java)

interface SenseCodeTasksListener {
    fun onNewTask(type: String, userMessage: ChatConversation.Message)
}