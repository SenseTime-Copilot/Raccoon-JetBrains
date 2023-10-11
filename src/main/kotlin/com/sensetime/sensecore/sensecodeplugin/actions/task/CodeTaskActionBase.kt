package com.sensetime.sensecore.sensecodeplugin.actions.task

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_TASKS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import kotlin.reflect.KClass

abstract class CodeTaskActionBase : AnAction() {
    private val key: String = getActionKey(this::class)
    override fun actionPerformed(e: AnActionEvent) {
        getEditorSelectedText(e.getData(CommonDataKeys.EDITOR))?.let { code ->
            promptTemplate?.let { promptTemplate ->
                val taskPromptPair = getTaskPromptPair(code, promptTemplate)
                ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_TASKS_TOPIC)
                    .onNewTask(taskPromptPair.first, taskPromptPair.second)
            }
        }
    }

    private val actionsModelConfig: ModelConfig?
        get() = CodeClientManager.getClientAndConfigPair().second.run { models[actionsModelName] }
    protected val promptTemplate: ModelConfig.PromptTemplate?
        get() = actionsModelConfig?.codeTaskActions?.get(key)

    protected fun getEditorSelectedText(editor: Editor?) =
        actionsModelConfig?.run { SenseCodeNotification.checkEditorSelectedText(maxInputTokens, editor) }

    private fun getTaskPromptPair(code: String, promptTemplate: ModelConfig.PromptTemplate): Pair<String, String?> =
        Pair(promptTemplate.displayText.format(code), promptTemplate.prompt?.format(code))

    companion object {
        @JvmStatic
        fun <T : CodeTaskActionBase> getActionKey(c: KClass<T>): String = c.simpleName!!
    }
}