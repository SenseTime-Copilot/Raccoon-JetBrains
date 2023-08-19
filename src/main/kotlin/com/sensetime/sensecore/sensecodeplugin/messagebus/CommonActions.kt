package com.sensetime.sensecore.sensecodeplugin.messagebus

import com.intellij.util.messages.Topic
import com.sensetime.sensecore.sensecodeplugin.ui.main.Tab

val COMMON_ACTIONS_TOPIC: Topic<CommonActions> = Topic.create("GptMentorCommonActionsTopic", CommonActions::class.java)

interface CommonActions {
    fun selectTab(tab: Tab)
}
