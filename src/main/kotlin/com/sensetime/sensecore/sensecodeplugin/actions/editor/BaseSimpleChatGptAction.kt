package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.domain.BasicPrompt
import com.sensetime.sensecore.sensecodeplugin.domain.PromptFactory
import com.sensetime.sensecore.sensecodeplugin.messagebus.CHAT_GPT_ACTION_TOPIC
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.sensetime.sensecore.sensecodeplugin.ui.GptMentorToolWindowFactory

abstract class BaseSimpleChatGptAction : AnAction() {
    protected lateinit var promptFactory: PromptFactory

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        val editor = e.getData(CommonDataKeys.EDITOR)

        if (project == null || editor == null) {
            return
        }

        ToolWindowManager.getInstance(project).getToolWindow(GptMentorToolWindowFactory.ID)?.show()

        if (!this::promptFactory.isInitialized) {
            val state = ApplicationManager.getApplication().getService(GptMentorSettingsState::class.java)
            promptFactory = PromptFactory(state)
        }

        val selectedText = editor.selectionModel.selectedText
        selectedText?.let { code ->
            val prompt = createPrompt(code)
            project.messageBus.syncPublisher(CHAT_GPT_ACTION_TOPIC).onNewPrompt(prompt)
        }
    }

    protected abstract fun createPrompt(code: String): BasicPrompt
}
