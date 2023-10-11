package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_TASKS_TOPIC = Topic.create("SenseCodeTasksTopic", SenseCodeTasksListener::class.java)

interface SenseCodeTasksListener {
    fun onNewTask(displayText: String, prompt: String? = null)
}