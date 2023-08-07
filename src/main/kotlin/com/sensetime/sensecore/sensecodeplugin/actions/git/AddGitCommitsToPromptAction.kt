package com.sensetime.sensecore.sensecodeplugin.actions.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.sensetime.sensecore.sensecodeplugin.common.extensions.addNewLinesIfNeeded
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.domain.PromptFactory
import com.sensetime.sensecore.sensecodeplugin.messagebus.CHAT_GPT_ACTION_TOPIC
import com.sensetime.sensecore.sensecodeplugin.ui.GptMentorToolWindowFactory

class AddGitCommitsToPromptAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return

        val commits = e.dataContext.getData("PRESET_COMMIT_MESSAGE")
            ?.toString()
            ?.split("\n")
            ?.reversed()
            ?.joinToString("\n")

        if (commits?.isNotEmpty() == true) {
            ToolWindowManager.getInstance(project).getToolWindow(GptMentorToolWindowFactory.ID)?.show()
            val state = ApplicationManager.getApplication().getService(GptMentorSettingsState::class.java)
            val promptFactory = PromptFactory(state)
            promptFactory.promptFromSelection(commits.addNewLinesIfNeeded(2)).let { prompt ->
                project.messageBus.syncPublisher(CHAT_GPT_ACTION_TOPIC).onNewPrompt(prompt)
            }
        }
    }
}
