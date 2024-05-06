package com.sensetime.sensecode.jetbrains.raccoon.tasks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCodeChunk
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.UserMessage
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_TASKS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import kotlin.reflect.KClass


internal abstract class CodeTaskActionBase : AnAction() {
    private val key: String = getActionKey(this::class)

    private fun getEditorSelectedText(project: Project, editor: Editor?): Pair<String, List<LLMCodeChunk>?>? =
        RaccoonNotification.checkEditorSelectedText(
            RaccoonClient.clientConfig.chatModelConfig.maxInputTokens,
            editor, project,
            false
        )

    protected fun sendNewTaskMessage(
        project: Project,
        code: String,
        language: String,
        args: Map<String, String>? = null,
        localKnowledge: List<LLMCodeChunk>?
    ) {
        UserMessage.createUserMessage(project, promptType = key, code = code, language = language, args = args)?.let {
            project.messageBus.syncPublisher(RACCOON_TASKS_TOPIC).onNewTask(it, localKnowledge)
        }
    }

    protected open fun sendNewTaskMessage(
        project: Project,
        editor: Editor,
        code: String,
        language: String,
        localKnowledge: List<LLMCodeChunk>?
    ) {
        sendNewTaskMessage(project, code, language, localKnowledge = localKnowledge)
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(CommonDataKeys.EDITOR)?.let { editor ->
            e.project?.let { p ->
                getEditorSelectedText(p, editor)?.let { (code, localKnowledge) ->
                    sendNewTaskMessage(
                        p,
                        editor,
                        code,
                        RaccoonLanguages.getMarkdownLanguageFromPsiFile(e.getData(CommonDataKeys.PSI_FILE)),
                        localKnowledge
                    )
                }
            }
        }
    }

    companion object {
        const val TASK_ACTIONS_GROUP_ID = "com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeTaskActionsGroup"

        @JvmStatic
        fun <T : CodeTaskActionBase> getActionKey(c: KClass<T>): String = c.simpleName!!
    }
}