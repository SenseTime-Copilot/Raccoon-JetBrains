package com.sensetime.sensecore.sensecodeplugin.actions.editor

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

class ManualTriggerCodeCompletionAction : BaseCodeInsightAction(false) {
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

        @JvmStatic
        private fun getContent(psiElement: PsiElement, caretOffsetInElement: Int, maxLength: Int): String {
            var code = psiElement.text
            var offset = caretOffsetInElement
            var curPsiElement = psiElement
            while (true) {
                if ((null == curPsiElement.parent) || (curPsiElement.parent.text.length > maxLength)) {
                    break
                }
                code = curPsiElement.parent.text
                offset += curPsiElement.startOffsetInParent
                if (curPsiElement.parent is PsiFile) {
                    break
                }
                curPsiElement = curPsiElement.parent
            }
            return if (code.length > offset) {
                """<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.

${code.substring(0, offset)}<fim_suffix>${code.substring(offset)}<fim_middle>""".trimIndent()
            } else {
                """<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.

$code<fim_middle><fim_suffix>""".trimIndent()
            }
        }
    }


    override fun getHandler(): CodeInsightActionHandler {
        return CodeInsightActionHandler { _: Project?, editor: Editor, psiFile: PsiFile? ->
            apiJob?.cancel()
            var caretOffset = editor.caretModel.offset
            apiJob = findPsiElementAt(psiFile, caretOffset)?.let { psiElement ->
                if (!isWhiteSpacePsi(psiElement)) {
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
                        content = getContent(psiElement, caretOffset - psiElement.textOffset, 2048)
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

                                        is StreamingResponse.Error -> {}
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