package com.sensetime.intellij.plugins.sensecode.tasks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.sensetime.intellij.plugins.sensecode.persistent.histories.UserMessage
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.topics.SENSE_CODE_TASKS_TOPIC
import com.sensetime.intellij.plugins.sensecode.ui.SenseCodeNotification
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeUtils
import kotlin.reflect.KClass

abstract class CodeTaskActionBase : AnAction() {
    private val key: String = getActionKey(this::class)

    private fun getEditorSelectedText(editor: Editor?): String? =
        SenseCodeNotification.checkEditorSelectedText(
            SenseCodeSettingsState.selectedClientConfig.toolwindowModelConfig.maxInputTokens,
            editor,
            false
        )

    protected fun sendNewTaskMessage(
        code: String, language: String, args: Map<String, String>? = null
    ) {
        UserMessage.createUserMessage(promptType = key, code = code, language = language, args = args)?.let {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_TASKS_TOPIC).onNewTask(it)
        }
    }

    protected open fun sendNewTaskMessage(editor: Editor, code: String, language: String) {
        sendNewTaskMessage(code, language)
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(CommonDataKeys.EDITOR)?.let { editor ->
            getEditorSelectedText(editor)?.let { code ->
                sendNewTaskMessage(
                    editor,
                    code,
                    SenseCodeUtils.getMarkdownLanguage(e.getData(CommonDataKeys.PSI_FILE))
                )
            }
        }
    }

    companion object {
        const val TASK_ACTIONS_GROUP_ID = "com.sensetime.intellij.plugins.sensecode.tasks.CodeTaskActionsGroup"

        @JvmStatic
        fun <T : CodeTaskActionBase> getActionKey(c: KClass<T>): String = c.simpleName!!
    }
}