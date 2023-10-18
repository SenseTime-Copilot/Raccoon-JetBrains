package com.sensetime.sensecore.sensecodeplugin.actions.task

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_TASKS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import kotlin.reflect.KClass

abstract class CodeTaskActionBase : AnAction() {
    open val raw: String = ""
    open val customArgs: Map<String, String>? = null
    private val key: String = getActionKey(this::class)
    private val actionsModelConfig: ModelConfig
        get() = CodeClientManager.getClientAndConfigPair().second.getModelConfigByType(key)

    override fun actionPerformed(e: AnActionEvent) {
        getEditorSelectedText(e.getData(CommonDataKeys.EDITOR))?.let { code ->
            sendNewTaskMessage(ChatConversation.Message.makeMessage(raw, code, customArgs))
        }
    }

    protected fun sendNewTaskMessage(userMessage: ChatConversation.Message) {
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_TASKS_TOPIC).onNewTask(key, userMessage)
    }

    protected fun getEditorSelectedText(editor: Editor?) =
        SenseCodeNotification.checkEditorSelectedText(actionsModelConfig.maxInputTokens, editor)

    companion object {
        @JvmStatic
        fun <T : CodeTaskActionBase> getActionKey(c: KClass<T>): String = c.simpleName!!
    }
}