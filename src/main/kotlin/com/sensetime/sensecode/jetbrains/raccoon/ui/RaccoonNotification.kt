package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.notification.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiUtilBase
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCodeChunk
import com.sensetime.sensecode.jetbrains.raccoon.llm.knowledgebases.CodeLocalContextFinder
import com.sensetime.sensecode.jetbrains.raccoon.llm.tokens.RaccoonTokenUtils
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank


internal object RaccoonNotification {
    val GROUP_ID: String = "${RaccoonPlugin.name} Notification Group"

    @JvmStatic
    val notificationGroup: NotificationGroup
        get() = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)

    @JvmStatic
    fun notifySettingsAction(notification: Notification, actionName: String) {
        notification.addAction(NotificationAction.createSimple(actionName) {
            RaccoonUIUtils.showRaccoonSettings()
        }).notify(null)
    }

    private var lastPopupMessage: String = ""

    @JvmStatic
    fun popupMessageInBestPositionForEditor(message: String, editor: Editor?, diffOnly: Boolean) {
        editor?.takeUnless { diffOnly && (message == lastPopupMessage) }?.let {
            JBPopupFactory.getInstance().createMessage(message).showInBestPositionFor(it)
        }
        lastPopupMessage = message
    }

    @JvmStatic
    fun popupNoCompletionSuggestionMessage(editor: Editor?, diffOnly: Boolean) {
        popupMessageInBestPositionForEditor(
            RaccoonBundle.message("completions.inline.warning.noCompletionSuggestion"),
            editor,
            diffOnly
        )
    }

    @JvmStatic
    fun checkEditorSelectedText(
        maxInputTokens: Int,
        editor: Editor?, project: Project,
        diffOnly: Boolean
    ): Pair<String, List<LLMCodeChunk>?>? =
        editor?.let {
            editor.selectionModel.let { selectionModel ->
                selectionModel.selectedText?.letIfNotBlank { selectedText ->
                    val curTokens = RaccoonTokenUtils.estimateTokensNumber(selectedText)
                    if (curTokens > maxInputTokens) {
                        popupMessageInBestPositionForEditor(
                            RaccoonBundle.message("notification.editor.selectedText.tooLong"),
                            editor,
                            diffOnly
                        )
                        null
                    } else {
                        Pair(
                            selectedText,
                            project.takeUnless { DumbService.isDumb(it) }?.let { notDumbProject ->
                                PsiUtilBase.getPsiFileInEditor(editor, notDumbProject)
                                    ?.takeIf { true }
                                    ?.let { psiFile ->
                                        CodeLocalContextFinder.findAllContextsLocally(
                                            psiFile,
                                            ((maxInputTokens - curTokens) * 0.75).toInt(),
                                            selectionModel.selectionStart,
                                            selectionModel.selectionEnd
                                        ).map { context ->
                                            LLMCodeChunk(context.first, context.second)
                                        }
                                    }
                            })
                    }
                }
            }
        }
}