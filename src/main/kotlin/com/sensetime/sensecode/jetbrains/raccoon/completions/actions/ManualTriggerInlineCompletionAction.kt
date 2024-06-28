package com.sensetime.sensecode.jetbrains.raccoon.completions.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
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
                    val prompt = getUserContent(
                        psiElement,
                        caretOffset - psiElement.textOffset,
                        modelConfig.maxInputTokens
                    ).getMessages(language, modelConfig)
                    LLMClientManager.getInstance(project)
                        .launchLLMCompletionJob(!RaccoonSettingsState.instance.isAutoCompleteMode,
                            editor.component,
                            LLMCompletionRequest(
                                settings.candidates,
                                prompt = prompt
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

        data class UserContent(var text: String, var offset: Int, val maxLength: Int, var knowledge: String = "") {

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

            private fun getPrefixArgs(prefix: String, knowledge: String = ""): Map<String, String> {
                var prefixLines = ""
                var prefixCursor = prefix
                prefix.lastIndexOf('\n').takeIf { it >= 0 }?.let {
                    prefixLines = prefix.substring(0, it + 1)
                    prefixCursor = prefix.substring(it + 1)
                }
                return mapOf(
                    "prefixLines" to knowledge + prefixLines,
                    "prefixCursor" to prefixCursor,
                    "prefix" to prefix
                )
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
                    ) + getPrefixArgs(text.substring(0, offset), knowledge)
                } else {
                    mapOf("language" to language, "suffixLines" to "", "suffixCursor" to "") + getPrefixArgs(
                        text
                    )
                }
            )
        }

        fun isFunctionKeyWord(str: String): String {
            println("it text ${str}")
            return if (!str.contains("class ")) str else ""
        }

        fun isFunctionCallRef(reference: PsiReference): Boolean {
            val ref = reference.toString()
            return when {
                ref.startsWith("KtSimpleNameReferenceDescriptorsImpl") -> true
                ref.startsWith("KtInvokeFunctionReferenceDescriptorsImpl") -> true
                ref.startsWith("PsiReferenceExpression:") -> true
                ref.contains("REFERENCE_EXPRESSION:") -> true
                ref.contains("JSReferenceExpression:") -> true
                ref.contains("PyReferenceExpression:") -> true
                else -> false
            }
        }

        fun isFunctionCall(element: PsiElement): Boolean {
            val nodeType = element.node.elementType.toString()
            val reference = element.reference.toString()
            return when {
                nodeType == "JS:REFERENCE_EXPRESSION" -> true
                nodeType == "Py:REFERENCE_EXPRESSION" -> {
                    if (reference.contains("PyReferenceExpression")) {
                        true
                    } else {
                        false
                    }
                }
                nodeType == "REFERENCE_EXPRESSION" -> true
//                reference.startsWith("KtInvokeFunctionReferenceDescriptorsImpl") -> true
//                reference.startsWith("KtSimpleNameReferenceDescriptorsImpl") -> true
//                reference.startsWith("KtInvokeFunctionReferenceDescriptorsImpl") -> true
                reference.startsWith("PsiReferenceExpression:") -> true
                else -> false
            }
        }

        private fun findFunctionDefinitions(
            project: Project,
            functionCalls: List<PsiElement>,
            fileName: String,
            foundDefinitions: MutableList<String>,
            maxLength: Int
        ) {
            val openFiles = FileEditorManager.getInstance(project).openFiles
            for (file in openFiles) {
                if (file.name == fileName) {
                    continue
                }
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile == null) {
                    continue
                }
                for (call in functionCalls){
                    call.references.mapNotNull { reference ->
                        val resolvedElement = reference.resolve()
                        val containingFile = resolvedElement?.containingFile
                        // 检查引用是否在打开的文件中
                        val isOpen = containingFile?.virtualFile?.let { file.name == it.name } == true
//                        val definition = resolvedElement?.text?.takeUnless { it.split("\n")[0].contains("class ") }?:""
                        val definition = isFunctionKeyWord(resolvedElement?.text?:"")
                        if (isOpen && definition != ""){
                            if (foundDefinitions.joinToString("\n").length + definition.length < maxLength) {
                                foundDefinitions.add(definition)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }

                }

            }
        }

        private fun getFunctionName(call: PsiElement): String? {
            // 获取函数名
            return call.text.split("(").firstOrNull()?.split(".")?.lastOrNull()
                ?.let { if (it.isEmpty()) call.text else it }
        }


        private fun findFunctionCalls(project: Project, file: VirtualFile, remainValue: Int): String {
            val foundDefinitions = mutableListOf<String>()
            ApplicationManager.getApplication().runReadAction {
                val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runReadAction
                // 查找当前文件中的所有函数调用
                val functionCalls = mutableListOf<PsiElement>()
                psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (isFunctionCall(element)) {
                            if (!functionCalls.any { it.text == element.text }) {
                                functionCalls.add(element)
                            }
                        }
                        super.visitElement(element)
                    }
                })
                println("functionCalls: $functionCalls")
                // 在其他打开的文件中查找这些函数调用的定义
                findFunctionDefinitions(project, functionCalls, file.name, foundDefinitions, remainValue)
            }
            return foundDefinitions.joinToString("\n")
        }


        @JvmStatic
        private fun getUserContent(psiElement: PsiElement, caretOffsetInElement: Int, maxLength: Int): UserContent {
            var userContent = UserContent(psiElement.text, caretOffsetInElement, maxLength)
            // 如果当前节点的文本长度大于最大长度，递归查找父节点
            if (userContent.cutByMaxLength()) {
                var curPsiElement = psiElement
                var nn = 0
                while (true) {
                    nn++
                    // 如果当前是文件，直接跳出
                    if (curPsiElement is PsiFile) {
                        break
                    }
                    // 如果当前是根节点，或者当前节点的文本长度大于最大长度，直接跳出
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
            val remainLengthValue = userContent.maxLength - userContent.text.length
            val pretext =
                findFunctionCalls(psiElement.project, psiElement.containingFile.virtualFile, remainLengthValue)
            println("search text: ${pretext} ... ")
            userContent.text = userContent.text
            userContent.knowledge = " \n " + pretext + " \n "
            return userContent
        }
    }
}