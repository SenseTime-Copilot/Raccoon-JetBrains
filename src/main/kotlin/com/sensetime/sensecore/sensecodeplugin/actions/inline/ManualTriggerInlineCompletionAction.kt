package com.sensetime.sensecore.sensecodeplugin.actions.inline

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.sensetime.sensecore.sensecodeplugin.completions.CompletionPreview
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.openapi.OpenApi
import com.sensetime.sensecore.sensecodeplugin.openapi.RealOpenApi
import com.sensetime.sensecore.sensecodeplugin.openapi.StreamingResponse
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import com.sensetime.sensecore.sensecodeplugin.openapi.request.chatGptRequest
import com.sensetime.sensecore.sensecodeplugin.security.GptMentorCredentialsManager
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ManualTriggerInlineCompletionAction : BaseCodeInsightAction(false), InlineCompletionAction {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiJob: Job? = null
    private val openApi: OpenApi = RealOpenApi(
        client = HttpClient(),
        okHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build(),
        credentialsManager = GptMentorCredentialsManager,
    )

    companion object {
        @JvmStatic
        private fun isWhiteSpacePsi(psiElement: PsiElement?): Boolean {
            return (null == psiElement) || (psiElement is PsiWhiteSpace)
        }

        @JvmStatic
        private fun findPsiElementAt(psiFile: PsiFile?, caretOffset: Int): PsiElement? {
            var result = psiFile?.findElementAt(caretOffset)
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

            fun getContent(): String {
                return if (text.length > offset) {
                    """<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.

${text.substring(0, offset)}<fim_suffix>${text.substring(offset)}<fim_middle>""".trimIndent()
                } else {
                    """<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.

$text<fim_middle><fim_suffix>""".trimIndent()
                }
            }
        }

        @JvmStatic
        private fun getUserContent(psiElement: PsiElement, caretOffsetInElement: Int, maxLength: Int): String {
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
                                appendPostOk && (nextPsiElement?.text?.let { userContent.tryAppendPost(it) } ?: false)
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
            return userContent.getContent()
        }
    }

    override fun getHandler(): CodeInsightActionHandler {
        return CodeInsightActionHandler { _: Project?, editor: Editor, psiFile: PsiFile? ->
            apiJob?.cancel()
            var caretOffset = editor.caretModel.offset
            apiJob = findPsiElementAt(psiFile, caretOffset)?.let { psiElement ->
                if (!isWhiteSpacePsi(psiElement) && psiElement.text.length <= 16) {
                    caretOffset = psiElement.endOffset
                }
                val completionPreview =
                    CompletionPreview.createInstance(editor, caretOffset)
                val state = GptMentorSettingsState.getInstance()
                val request = chatGptRequest {
                    this.temperature = state.temperature
                    this.maxTokens = state.maxTokens
                    this.model = state.model
                    this.systemPrompt("")
                    message {
                        role = ChatGptRequest.Message.Role.USER
                        content = getUserContent(psiElement, caretOffset - psiElement.textOffset, 2048)
                    }
                    stream = true
                }
                scope.launch {
                    kotlin.runCatching {
                        openApi.executeBasicActionStreaming(request)
                            .collect { streamingResponse ->
                                ApplicationManager.getApplication().invokeLater {
                                    when (streamingResponse) {
                                        is StreamingResponse.Data -> completionPreview.appendCompletions(
                                            listOf(
                                                streamingResponse.data
                                            )
                                        )

                                        is StreamingResponse.Error -> {
                                            completionPreview.showError(streamingResponse.error)
                                        }

                                        StreamingResponse.Done -> completionPreview.done = true
                                    }
                                }
                            }
                    }.onFailure {
                        if (it !is CancellationException) {
                            it.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun isValidForLookup(): Boolean = true
}