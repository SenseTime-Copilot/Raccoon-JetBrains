package com.sensetime.sensecore.sensecodeplugin.ui.main

import com.intellij.openapi.project.Project
import com.sensetime.sensecore.sensecodeplugin.messagebus.CHAT_GPT_ACTION_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messagebus.COMMON_ACTIONS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.openapi.RealOpenApi
import java.awt.Desktop
import javax.swing.event.HyperlinkEvent

class MainPresenter() {
    private lateinit var project: Project
    fun onAttach(project: Project) {
        this.project = project
    }

    fun selectTab(tab: Tab) {
        project.messageBus.syncPublisher(COMMON_ACTIONS_TOPIC).selectTab(tab)
    }

    fun openUrl(event: HyperlinkEvent) {
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            try {
                Desktop.getDesktop().browse(event.url.toURI())
            } catch (ex: Exception) {
                logger.error(ex)
            }
        }
    }

    companion object {
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RealOpenApi::class.java)
    }
}
