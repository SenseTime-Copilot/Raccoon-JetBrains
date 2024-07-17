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
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.FunctionCallInfo
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LocalKnows
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.PromptData
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMCompletionChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMResponse
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.CompletionPreview
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeTaskActionBase
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
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
//                    if (!isWhiteSpacePsi(psiElement) && psiElement.text.length <= 16) {
//                        caretOffset = psiElement.endOffset
//                    }
                    val language = RaccoonLanguages.getMarkdownLanguageFromPsiFile(psiFile)
                    val completionPreview =
                        CompletionPreview.createInstance(editor, caretOffset, language)

                    val settings = RaccoonSettingsState.instance
                    val modelConfig = RaccoonClient.clientConfig.completionModelConfig
                    val isSingleLine =
                        (settings.inlineCompletionPreference == CompletionModelConfig.CompletionPreference.SPEED_PRIORITY)
                    val userContent = getUserContent(
                        psiElement,
                        caretOffset - psiElement.textOffset,
                        modelConfig.maxInputTokens
                    )
                    val prompt = userContent.getMessages(language, modelConfig)
                    LLMClientManager.getInstance(project)
                        .launchLLMCompletionJob(!RaccoonSettingsState.instance.isAutoCompleteMode,
                            editor.component,
                            LLMCompletionRequest(
                                settings.candidates,
                                prompt = prompt,
                                knowledgeJSON = userContent.knowledgeJSON
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

        data class UserContent(var text: String, var offset: Int, val maxLength: Int, var knowledge: String = "", var knowledgeJSON: LocalKnows? = null) {

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
            ): PromptData {
                val prefix = knowledge + text.substring(0, offset)
                val suffix = text.substring(offset)
                return PromptData(language, prefix, suffix)
//                if (text.length > offset) {
//                    val suffix = text.substring(offset)
//                    var suffixLines = ""
//                    var suffixCursor = suffix.trimEnd('\r', '\n')
//                    suffix.indexOf('\n').takeIf { it >= 0 }?.let {
//                        suffixLines = suffix.substring(it + 1)
//                        suffixCursor = suffix.substring(0, it).trimEnd('\r', '\n')
//                    }
//                    println( "suffixCursor: $suffixCursor suffixLines: $suffixLines suffix: $suffix")
//                    mapOf(
//                        "language" to language,
//                        "suffixLines" to suffixLines,
//                        "suffixCursor" to suffixCursor,
//                        "suffix" to suffix
//                    ) + getPrefixArgs(text.substring(0, offset), knowledge)
//                } else {
//                    mapOf("language" to language, "suffixLines" to "", "suffixCursor" to "") + getPrefixArgs(
//                        text
//                    )
//                }
            }
        }

        fun isFunctionOrMethodKeyWord(str: String): String {
            val classPattern = Regex("""(public\s+)?class\s+(\w+)""")
            val javaMethodPattern = Regex("""(public|private|protected)?\s+(static\s+)?(void|int|String|char|boolean|double|float|long|short|byte|[A-Za-z_][\w]*)\s+(\w+)\s*\(([^)]*)\)""")
            val kotlinMethodPattern = Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*(:\s*[\w<>,\s]*\??)?""")
            val pythonMethodPattern = Regex("""def\s+(\w+)\s*\(([^)]*)\)\s*(->\s*([\w\[\],\s]*))?:?\s*""")

            val jsMethodPattern = Regex("""function\s+(\w+)\s*\(([^)]*)\)""")

            var classSignature: String? = null
            val methodSignatures = mutableListOf<String>()


            val lines = str.lines()
            if (lines.isNotEmpty()) {
                classPattern.find(lines[0])?.let {
                    classSignature = "class ${it.groupValues[2]}"
                }
            }
            // java 与 kotlin 区分不出来，所以加个判断
            // Find method signatures (Kotlin)
            if (str.contains("fun ")) {
                kotlinMethodPattern.findAll(str).forEach { matchResult ->
                    val methodName = matchResult.groupValues[1]
                    val parameters = matchResult.groupValues[2]
                    val returnType = matchResult.groupValues[3].takeIf { it.isNotEmpty() }?.let { ": ${it.trim()}" } ?: ""
                    methodSignatures.add("fun $methodName($parameters)$returnType")
                }
            } else if (str.contains("class ") || str.contains("public ") || str.contains("private ") || str.contains("protected ")) {
                // Find method signatures (Java)
                javaMethodPattern.findAll(str).forEach { matchResult ->
                    val accessModifier = matchResult.groupValues[1].takeIf { it.isNotEmpty() }?.let { "$it " } ?: ""
                    val staticModifier = matchResult.groupValues[2].takeIf { it.isNotEmpty() }?.let { "$it " } ?: ""
                    val returnType = matchResult.groupValues[3]
                    val methodName = matchResult.groupValues[4]
                    val parameters = matchResult.groupValues[5]

                    methodSignatures.add("$accessModifier$staticModifier$returnType $methodName($parameters)")
                }
            }
            else if (str.contains("def ")) {
                pythonMethodPattern.findAll(str).forEach { matchResult ->
                    val methodName = matchResult.groupValues[1]
                    val parameters = matchResult.groupValues[2]
                    val returnType =
                        matchResult.groupValues[4].takeIf { it.isNotEmpty() }?.let { " -> ${it.trim()}" } ?: ""
                    println("def $methodName($parameters)$returnType")
                    methodSignatures.add("def $methodName($parameters)$returnType")
                }
            }
            // Check for JavaScript methods
            else if (str.contains("function ")) {

            // Find method signatures (JavaScript)
            jsMethodPattern.findAll(str).forEach { matchResult ->
                val methodName = matchResult.groupValues[1]
                val parameters = matchResult.groupValues[2]

                methodSignatures.add("function $methodName($parameters)")
            }}

            val combinedSignatures = StringBuilder()
            classSignature?.let { combinedSignatures.append(it).append("\n ") }
            methodSignatures.forEach { combinedSignatures.append(it).append("\n ") }
            println(combinedSignatures.toString())
            return combinedSignatures.toString()
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

//        fun isFunctionCall(element: PsiElement): Boolean {
//            val nodeType = element.node.elementType.toString()
//            val reference = element.reference.toString()
//
//            val ignoredTypes = listOf(
//                "String", "Number", "Integer", "Boolean", "Double", "Float", "Long", "Short", "Byte", "Character",
//                "StringBuilder", "StringBuffer", "ArrayList", "HashMap", "HashSet", "LinkedList", "Array", "kotlin",
//                "jvm", "JvmStatic"
//            )
//
//            if (ignoredTypes.any { reference.contains(it) }) return false
//
//            return when (nodeType) {
//                "JS:REFERENCE_EXPRESSION", "REFERENCE_EXPRESSION" -> true
//                "Py:REFERENCE_EXPRESSION" -> reference.contains("PyReferenceExpression")
//                "JAVA_CODE_REFERENCE" -> reference.startsWith("PsiJavaCodeReferenceElement:")
//                else -> false
//            }
//        }

        fun isFunctionCall(element: PsiElement): Boolean {
            val nodeType = element.node.elementType.toString()
            val reference = element.reference.toString()
            // 定义需要忽略的基础类型和标准库类
            val ignoredTypes = listOf(
                "String", "Number", "Integer", "Boolean", "Double", "Float", "Long", "Short", "Byte", "Character",
                "StringBuilder", "StringBuffer", "ArrayList", "HashMap", "HashSet", "LinkedList", "Array", "kotlin",
                "jvm", "JvmStatic"
            )
            println("reference: $reference , nodeType: $nodeType")

            if (ignoredTypes.any { reference.contains(it) }) return false
            return when (nodeType) {
                "JS:REFERENCE_EXPRESSION" -> true
                "Py:REFERENCE_EXPRESSION" -> reference.contains("PyFromImportSourceReference")
                "JAVA_CODE_REFERENCE" -> reference.startsWith("PsiJavaCodeReferenceElement:")
                "REFERENCE_EXPRESSION" -> !reference.contains("PsiReferenceExpression")
                else -> false
            }

        }

        private fun findFunctionDefinitions(
            project: Project,
            functionCalls: List<PsiElement>,
            fileName: String,
            foundDefinitions: MutableList<FunctionCallInfo>,
            maxLength: Int
        ) {
            functionCalls.forEach { call ->
                call.references.mapNotNull { reference ->
                    reference.resolve()?.let { resolvedElement ->
                        resolvedElement.containingFile?.virtualFile?.name.takeIf { it != fileName }?.let {
                            isFunctionOrMethodKeyWord(resolvedElement.text ?: "").takeIf { definition ->
                                definition.isNotEmpty() && (foundDefinitions.joinToString("\n").length + definition.length < maxLength)
                            }?.let { definition ->
                                // 获取definition的第一行
                                val firstLine = definition.lineSequence().firstOrNull()

                                // 检查foundDefinitions中是否已经存在相同第一行的definition
                                if (foundDefinitions.none { it.file_chunk.lineSequence().firstOrNull() == firstLine }) {
                                    foundDefinitions.add(FunctionCallInfo(it, definition))
                                }
                            }
                        }
                    }
                }
            }
        }

//        private fun findFunctionDefinitions(
//            project: Project,
//            functionCalls: List<PsiElement>,
//            fileName: String,
//            foundDefinitions: MutableList<String>,
//            maxLength: Int
//        ) {
//            val openFiles = FileEditorManager.getInstance(project).openFiles
////            for (file in openFiles) {
////                if (file.name == fileName) {
////                    continue
////                }
////                val psiFile = PsiManager.getInstance(project).findFile(file)
////                if (psiFile == null) {
////                    continue
////                }
//                for (call in functionCalls){
//                    call.references.mapNotNull { reference ->
//                        val resolvedElement = reference.resolve()
//                        val containingFile = resolvedElement?.containingFile
//                        if (containingFile?.virtualFile?.name == fileName) {
//                            return
//                        }
//                        println(resolvedElement?.text)
//                        // 检查引用是否在打开的文件中
////                        val isOpen = containingFile?.virtualFile?.let { file.name == it.name } == true
////                        val definition = resolvedElement?.text?.takeUnless { it.split("\n")[0].contains("class ") }?:""
//                        val definition = isFunctionOrMethodKeyWord(resolvedElement?.text?:"")
//                        println("definition: $definition")
//                        if (definition != ""){
//                            if (foundDefinitions.joinToString("\n").length + definition.length < maxLength) {
//                                foundDefinitions.add(definition)
//                            } else {
//                                null
//                            }
//                        } else {
//                            null
//                        }
//                    }
//
//                }
//
////            }
//        }

        private fun getFunctionName(call: PsiElement): String? {
            // 获取函数名
            return call.text.split("(").firstOrNull()?.split(".")?.lastOrNull()
                ?.let { if (it.isEmpty()) call.text else it }
        }


        private fun findFunctionCalls(project: Project, file: VirtualFile, remainValue: Int): String {
            val foundDefinitions = mutableListOf<FunctionCallInfo>()
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
            val promptJson = Json.encodeToJsonElement(mapOf("local_knows" to foundDefinitions))
            return promptJson.toString()
//            return foundDefinitions.joinToString("\n")
        }


        fun extractAndJoin(localKnows: LocalKnows): String {
            return localKnows.local_knows.joinToString(separator = "\n") {
                // 使用注释形式输出文件名和文件内容
                "// File: ${it.file_name}\n${it.file_chunk.lines().joinToString(separator = "\n") { line -> "// $line" }}"
            }
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
            if(RaccoonSettingsState.instance.isLocalKnowledgeBaseEnabled) {
                val pretext =
                    findFunctionCalls(psiElement.project, psiElement.containingFile.virtualFile, remainLengthValue)
//                val jsonString = Json.encodeToString(pretext)
                val localKnows: LocalKnows = Json.decodeFromString(LocalKnows.serializer(), pretext)
                val resultString = extractAndJoin(localKnows)
                userContent.knowledge = " \n " + resultString + " \n "
                userContent.knowledgeJSON = localKnows
                println("search text: ${resultString} ... ")
            }
            userContent.text = userContent.text
            return userContent
        }
    }
}