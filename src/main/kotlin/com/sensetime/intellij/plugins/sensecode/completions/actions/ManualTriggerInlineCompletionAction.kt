package com.sensetime.intellij.plugins.sensecode.completions.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.clients.requests.CodeRequest
import com.sensetime.intellij.plugins.sensecode.clients.responses.CodeStreamResponse
import com.sensetime.intellij.plugins.sensecode.completions.preview.CompletionPreview
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.tasks.CodeTaskActionBase
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class ManualTriggerInlineCompletionAction : BaseCodeInsightAction(false), Disposable, InlineCompletionAction {
    private var inlineCompletionJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun dispose() {
        inlineCompletionJob = null
    }

    private fun inlineCompletion(editor: Editor, psiFile: PsiFile?) {
        var caretOffset = editor.caretModel.offset
        inlineCompletionJob = findPsiElementAt(psiFile, caretOffset)?.let { psiElement ->
            if (!isWhiteSpacePsi(psiElement) && psiElement.text.length <= 16) {
                caretOffset = psiElement.endOffset
            }
            val completionPreview =
                CompletionPreview.createInstance(editor, caretOffset)

            val settings = SenseCodeSettingsState.instance
            val n = settings.candidates
            val (client, clientConfig) = SenseCodeClientManager.clientAndConfigPair
            val modelConfig = clientConfig.inlineModelConfig
            val codeRequest = CodeRequest(
                modelConfig.name,
                getUserContent(
                    psiElement,
                    caretOffset - psiElement.textOffset,
                    modelConfig.maxInputTokens
                ).getMessages(SenseCodeUtils.getMarkdownLanguage(psiElement), modelConfig),
                modelConfig.temperature,
                n,
                modelConfig.stop,
                modelConfig.getMaxNewTokens(settings.inlineCompletionPreference),
                clientConfig.inlineApiPath
            )
            SenseCodeClientManager.clientCoroutineScope.launch {
                try {
                    if (n <= 1) {
                        // stream
                        client.requestStream(codeRequest) { streamResponse ->
                            SenseCodeUIUtils.invokeOnUIThreadLater {
                                when (streamResponse) {
                                    CodeStreamResponse.Done -> completionPreview.done = true
                                    is CodeStreamResponse.Error -> completionPreview.showError(streamResponse.error)
                                    is CodeStreamResponse.TokenChoices -> {
                                        if (null == completionPreview.appendCompletions(
                                                listOf(
                                                    streamResponse.choices.firstOrNull()?.token ?: ""
                                                )
                                            )
                                        ) {
                                            cancel()
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } else {
                        client.request(codeRequest).run {
                            SenseCodeUIUtils.invokeOnUIThreadLater {
                                error?.takeIf { it.hasError() }?.let {
                                    completionPreview.showError(it.getShowError())
                                } ?: choices?.let { choices ->
                                    completionPreview.appendCompletions(choices.map { it.token ?: "" })
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        SenseCodeUIUtils.invokeOnUIThreadLater {
                            completionPreview.showError(t.localizedMessage)
                        }
                    }
                } finally {
                    SenseCodeUIUtils.invokeOnUIThreadLater {
                        completionPreview.done = true
                    }
                }
            }
        }
    }

    private fun popupCodeTaskActionsGroup(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            JBPopupFactory.getInstance().createActionGroupPopup(
                SenseCodePlugin.NAME,
                (ActionManager.getInstance().getAction(CodeTaskActionBase.TASK_ACTIONS_GROUP_ID) as ActionGroup),
                DataManager.getInstance().getDataContext(editor.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
            ).showInBestPositionFor(editor)
        }
    }

    override fun getHandler(): CodeInsightActionHandler {
        return CodeInsightActionHandler { _: Project?, editor: Editor, psiFile: PsiFile? ->
            if (!SenseCodeSettingsState.instance.isAutoCompleteMode || ((null == CompletionPreview.getInstance(editor)) && (editor.contentComponent.isFocusOwner))) {
                EditorUtil.disposeWithEditor(editor, this)

                val selectedText = editor.selectionModel.selectedText
                if (selectedText.isNullOrBlank()) {
                    inlineCompletion(editor, psiFile)
                } else {
                    popupCodeTaskActionsGroup(editor)
                }
            }
        }
    }

    override fun isValidForLookup(): Boolean = true

    companion object {
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
                    prefixLines = prefix.substring(0, it)
                    prefixCursor = prefix.substring(it + 1)
                }
                return mapOf("prefixLines" to prefixLines, "prefixCursor" to prefixCursor, "prefix" to prefix)
            }

            fun getMessages(
                language: String,
                modelConfig: ModelConfig
            ): List<CodeRequest.Message> = listOfNotNull(
                modelConfig.getSystemPromptPair()
                    ?.let { CodeRequest.Message(it.first, it.second) }) + CodeRequest.Message(
                modelConfig.getRoleString(ModelConfig.Role.USER),
                modelConfig.getPromptTemplate(ModelConfig.INLINE_COMPLETION)!!.toRawText(
                    if (text.length > offset) {
                        val suffix = text.substring(offset)
                        mapOf(
                            "language" to language,
                            "suffixLines" to suffix.trimStart('\r', '\n'),
                            "suffix" to suffix
                        ) + getPrefixArgs(text.substring(0, offset))
                    } else {
                        mapOf("language" to language, "suffixLines" to "") + getPrefixArgs(text)
                    }
                )
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