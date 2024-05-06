package com.sensetime.sensecode.jetbrains.raccoon.completions.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMCompletionChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.CompletionPreview
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeTaskActionBase
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import kotlinx.coroutines.Job
import kotlin.math.min


internal class ManualTriggerInlineCompletionAction : BaseCodeInsightAction(false), Disposable, InlineCompletionAction {
    private var inlineCompletionJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun dispose() {
        inlineCompletionJob = null
    }

    private fun getToken(token: String?, isSingleLine: Boolean): String =
        token?.let { if (isSingleLine) it.trimEnd('\r', '\n') else it } ?: ""

    private var lastCaretOffset: Int = -1
    private fun inlineCompletion(project: Project, editor: Editor, psiFile: PsiFile?) {
        var caretOffset = editor.caretModel.offset
        if ((lastCaretOffset == caretOffset) && !isKeyboardShortcutAction) {
            return
        }
        lastCaretOffset = caretOffset
        inlineCompletionJob =
            findPsiElementAt(
                psiFile,
                caretOffset
            )?.takeIf {
                isKeyboardShortcutAction || (null != psiFile?.takeIf {
                    (caretOffset >= (psiFile.textLength)) || isLineSeparator(
                        psiFile.text[caretOffset]
                    )
                })
            }
                ?.let { psiElement ->
                    if (!isWhiteSpacePsi(psiElement) && psiElement.text.length <= 16) {
                        caretOffset = psiElement.endOffset
                    }
                    val language = RaccoonLanguages.getMarkdownLanguageFromPsiFile(psiFile)
                    val completionPreview =
                        CompletionPreview.createInstance(editor, caretOffset, language)

                    val settings = RaccoonSettingsState.instance
                    val modelConfig = RaccoonClient.clientConfig.completionModelConfig
                    val isSingleLine =
                        (settings.inlineCompletionPreference == CompletionModelConfig.CompletionPreference.SPEED_PRIORITY)
                    LLMClientManager.getInstance(project)
                        .launchLLMCompletionJob(!RaccoonSettingsState.instance.isAutoCompleteMode,
                            editor.component,
                            LLMCompletionRequest(
                                settings.candidates,
                                prompt = getUserContent(
                                    psiElement,
                                    caretOffset - psiElement.textOffset,
                                    modelConfig.maxInputTokens
                                ).getMessages(language, modelConfig)
                            ), object : LLMClientManager.LLMJobListener<LLMCompletionChoice, String?>,
                                LLMClient.LLMUsagesResponseListener<LLMCompletionChoice>() {
                                override fun onResponseInsideEdtAndCatching(llmResponse: LLMResponse<LLMCompletionChoice>) {
                                    llmResponse.throwIfError()
                                    llmResponse.choices?.let { choicesList ->
                                        if (null == completionPreview.appendCompletions(choicesList.map {
                                                getToken(it.token, isSingleLine)
                                            })
                                        ) {
                                            inlineCompletionJob = null
                                        }
                                    }
                                    super.onResponseInsideEdtAndCatching(llmResponse)
                                }

                                override fun onDoneInsideEdtAndCatching(): String? {
                                    completionPreview.done = true
                                    return super.onDoneInsideEdtAndCatching()
                                }

                                override fun onFailureWithoutCancellationInsideEdt(t: Throwable) {
                                    completionPreview.showError(t.localizedMessage)
                                }

                                override fun onFinallyInsideEdt() {
                                    completionPreview.done = true
                                }
                            })
                }
    }

    private fun popupCodeTaskActionsGroup(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            JBPopupFactory.getInstance().createActionGroupPopup(
                RaccoonPlugin.name,
                (ActionManager.getInstance().getAction(CodeTaskActionBase.TASK_ACTIONS_GROUP_ID) as ActionGroup),
                DataManager.getInstance().getDataContext(editor.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
            ).showInBestPositionFor(editor)
        }
    }

    private var isKeyboardShortcutAction: Boolean = false
    override fun actionPerformed(e: AnActionEvent) {
        isKeyboardShortcutAction = (e.place == ActionPlaces.KEYBOARD_SHORTCUT)
        super.actionPerformed(e)
    }

    override fun getHandler(): CodeInsightActionHandler {
        return CodeInsightActionHandler { project: Project, editor: Editor, psiFile: PsiFile? ->
            if (!RaccoonSettingsState.instance.isAutoCompleteMode || ((null == CompletionPreview.getInstance(editor)) && (editor.contentComponent.isFocusOwner))) {
                EditorUtil.disposeWithEditor(editor, this)

                val selectedText = editor.selectionModel.selectedText
                if (selectedText.isNullOrBlank()) {
                    inlineCompletion(project, editor, psiFile)
                } else if (isKeyboardShortcutAction) {
                    popupCodeTaskActionsGroup(editor)
                }
            }
        }
    }

    override fun isValidForLookup(): Boolean = true

    companion object {
        @JvmStatic
        private fun isLineSeparator(c: Char): Boolean = ((c == '\n') || (c == '\r'))

        @JvmStatic
        private fun isWhiteSpacePsi(psiElement: PsiElement?): Boolean {
            return (null == psiElement) || (psiElement is PsiWhiteSpace)
        }

        @JvmStatic
        private fun findPsiElementAt(psiFile: PsiFile?, caretOffset: Int): PsiElement? {
            var result = psiFile?.run { findElementAt(min(caretOffset, textLength - 1)) }
            if (isWhiteSpacePsi(result) && (caretOffset > 0) && ((null == result) || (caretOffset == result.textOffset))) {
                val prePsiElement = psiFile?.findElementAt(caretOffset - 1)
                if (!isWhiteSpacePsi(prePsiElement)) {
                    result = prePsiElement
                }
            }
            return result
        }

        data class UserContent(var text: String, var offset: Int, val maxLength: Int) {
            fun cutByMaxLength(preScale: Float = 0.7f): Boolean {
                if (maxLength >= text.length) {
                    return true
                }
                if (offset >= preScale * text.length) {
                    val endIndex = text.length.coerceAtMost(offset + ((1.0f - preScale) * text.length).toInt())
                    val startIndex = 0.coerceAtLeast(endIndex - maxLength)
                    text = text.substring(startIndex, endIndex)
                    offset -= startIndex
                } else {
                    text = text.substring(0, maxLength)
                }
                return false
            }

            fun tryAppendPre(preText: String): Boolean {
                if (text.length + preText.length > maxLength) {
                    return false
                }
                text = preText + text
                offset += preText.length
                return true
            }

            fun tryAppendPost(postText: String): Boolean {
                if (text.length + postText.length > maxLength) {
                    return false
                }
                text += postText
                return true
            }

            private fun getPrefixArgs(prefix: String): Map<String, String> {
                var prefixLines = ""
                var prefixCursor = prefix
                prefix.lastIndexOf('\n').takeIf { it >= 0 }?.let {
                    prefixLines = prefix.substring(0, it + 1)
                    prefixCursor = prefix.substring(it + 1)
                }
                return mapOf("prefixLines" to prefixLines, "prefixCursor" to prefixCursor, "prefix" to prefix)
            }

//            listOfNotNull(
//            modelConfig.getSystemPromptPair()
//            ?.let { CodeRequest.Message(it.first, it.second) })

            fun getMessages(
                language: String,
                modelConfig: CompletionModelConfig
            ): String = modelConfig.getPrompt(
                if (text.length > offset) {
                    val suffix = text.substring(offset)
                    var suffixLines = ""
                    var suffixCursor = suffix.trimEnd('\r', '\n')
                    suffix.indexOf('\n').takeIf { it >= 0 }?.let {
                        suffixLines = suffix.substring(it + 1)
                        suffixCursor = suffix.substring(0, it).trimEnd('\r', '\n')
                    }
                    mapOf(
                        "language" to language,
                        "suffixLines" to suffixLines,
                        "suffixCursor" to suffixCursor,
                        "suffix" to suffix
                    ) + getPrefixArgs(text.substring(0, offset))
                } else {
                    mapOf("language" to language, "suffixLines" to "", "suffixCursor" to "") + getPrefixArgs(
                        text
                    )
                }
            )
        }

        @JvmStatic
        private fun getUserContent(psiElement: PsiElement, caretOffsetInElement: Int, maxLength: Int): UserContent {
            var userContent = UserContent(psiElement.text, caretOffsetInElement, maxLength)
            if (userContent.cutByMaxLength()) {
                var curPsiElement = psiElement
                while (true) {
                    if (curPsiElement is PsiFile) {
                        break
                    }
                    if ((null == curPsiElement.parent) || (curPsiElement.parent.text.length > maxLength)) {
                        var appendPreOk = true
                        var appendPostOk = true
                        var prePsiElement = curPsiElement.prevSibling
                        var nextPsiElement = curPsiElement.nextSibling
                        while (true) {
                            appendPreOk =
                                appendPreOk && (prePsiElement?.text?.let { userContent.tryAppendPre(it) } ?: false)
                            appendPostOk =
                                appendPostOk && (nextPsiElement?.text?.let { userContent.tryAppendPost(it) }
                                    ?: false)
                            if (!appendPreOk && !appendPostOk) {
                                break
                            }
                            if (appendPreOk) {
                                prePsiElement = prePsiElement?.prevSibling
                            }
                            if (appendPostOk) {
                                nextPsiElement = nextPsiElement?.nextSibling
                            }
                        }
                        break
                    }
                    userContent = UserContent(
                        curPsiElement.parent.text,
                        userContent.offset + curPsiElement.startOffsetInParent,
                        maxLength
                    )
                    curPsiElement = curPsiElement.parent
                }
            }
            return userContent
        }
    }
}