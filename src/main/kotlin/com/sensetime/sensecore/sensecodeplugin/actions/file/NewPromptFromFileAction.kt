package com.sensetime.sensecore.sensecodeplugin.actions.file

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.domain.PromptFactory
import com.sensetime.sensecore.sensecodeplugin.messagebus.CHAT_GPT_ACTION_TOPIC

class NewPromptFromFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        if (project == null || files.isNullOrEmpty()) {
            return
        }

        val allFilesContent = buildString {
            files.forEach { file ->
                val contents = String(file.contentsToByteArray())
                append(contents)
                repeat(2) {
                    appendLine()
                }
            }
        }

        val promptFactory = PromptFactory(project.getService(GptMentorSettingsState::class.java))
        promptFactory.promptFromSelection(allFilesContent).let { prompt ->
            project.messageBus.syncPublisher(CHAT_GPT_ACTION_TOPIC).onNewPrompt(prompt)
        }
    }
}
