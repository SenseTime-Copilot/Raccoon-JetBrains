package com.sensetime.sensecode.jetbrains.raccoon.llm.knowledgebases

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction.Companion.isFunctionCallRef
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction.Companion.isFunctionKeyWord
import com.sensetime.sensecode.jetbrains.raccoon.llm.tokens.RaccoonTokenUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import java.util.LinkedList


internal object CodeLocalContextFinder {
    private fun ArrayList<PsiElement>.appendAllTopLevelElementsInsideRange(
        totalRange: TextRange, psiElements: Array<out PsiElement>
    ) {
        for (psiElement in psiElements) {
            psiElement.takeIf { it.isValid && (it !is PsiWhiteSpace) }?.textRange?.let { curRange ->
                when {
                    totalRange.contains(curRange) -> add(psiElement)
                    totalRange.intersectsStrict(curRange) -> psiElement.children.takeIf { it.isNotEmpty() }
                        ?.let { appendAllTopLevelElementsInsideRange(totalRange, it) } ?: add(psiElement)

                    else -> Unit
                }
            }
        }
    }

    private fun getAllTopLevelElements(psiFile: PsiFile, totalRange: TextRange): List<PsiElement> =
        ArrayList<PsiElement>().apply {
            appendAllTopLevelElementsInsideRange(totalRange, psiFile.children)
        }

    private val classTypeNames: List<String> = listOf("class")
    private val functionTypeNames: List<String> = listOf("fun", "method")
    private val functionBlockTypeNames: List<String> = listOf("block")

    private fun IElementType?.containsAnyNames(names: List<String>): Boolean =
        (true == this?.toString()?.letIfNotBlank { type -> names.any { type.contains(it, true) } })

    private fun IElementType?.isClass(): Boolean = containsAnyNames(classTypeNames)
    private fun IElementType?.isFunction(): Boolean = containsAnyNames(functionTypeNames)
    private fun IElementType?.isFunctionBlock(): Boolean = containsAnyNames(functionBlockTypeNames)

    private fun PsiElement.takeTextIfNotInsideRange(totalRange: TextRange?): String? =
        text.takeUnless { (true == totalRange?.contains(textRange)) }

    //    private fun PsiElement.getFunctionSignature(totalRange: TextRange?): String = StringBuilder().apply {
//        for (cur in children) {
//            if (!cur.elementType.isFunctionBlock()) {
//                cur.takeTextIfNotInsideRange(totalRange)?.let { append(it) }
//            }
//        }
//    }.toString().ifBlank { takeTextIfNotInsideRange(totalRange) ?: "" }
    private fun PsiElement.getFunctionSignature(totalRange: TextRange?): String =
        takeTextIfNotInsideRange(totalRange) ?: ""

    //    private fun PsiElement.getClassSummary(totalRange: TextRange?): String = StringBuilder().apply {
//        for (cur in children) {
//            cur.elementType.run {
//                when {
//                    isClass() -> append(cur.getClassSummary(totalRange))
//                    isFunction() -> append(cur.getFunctionSignature(totalRange))
//                    else -> cur.takeTextIfNotInsideRange(totalRange)?.let { append(it) }
//                }
//            }
//        }
//    }.toString().ifBlank { takeTextIfNotInsideRange(totalRange) ?: "" }
    private fun PsiElement.getClassSummary(totalRange: TextRange?): String = takeTextIfNotInsideRange(totalRange) ?: ""

    private fun PsiElement.insideFile(psiFile: PsiFile): Boolean = containingFile == psiFile

    private fun PsiElement.takeIfNotDuplicate(allPsiElements: ArrayList<PsiElement>): PsiElement? =
        takeUnless { curPsiElement ->
            allPsiElements.any {
                PsiTreeUtil.isAncestor(
                    it,
                    curPsiElement,
                    false
                )
            }
        }?.also { curPsiElement -> allPsiElements.add(curPsiElement) }

    private fun PsiElement.getContexts(
        psiFile: PsiFile,
        totalRange: TextRange,
        allPsiElements: ArrayList<PsiElement>
    ): List<Pair<String, String>> =
        references.mapNotNull { reference ->
            if(isFunctionCallRef(reference)) {
                // TODO token 计算需要再看看
                val resolve = reference.takeUnless { it.isSoft }?.resolve()
                val definition = resolve?.let {
                    it.text?.let { text ->
                        isFunctionKeyWord(text)
                    } }
                val openFiles = FileEditorManager.getInstance(psiFile.project).openFiles.map { it.name }
                if (definition != null && definition != "" && resolve.containingFile.name != psiFile.name && resolve.containingFile.name in openFiles) {
                    Pair(resolve.containingFile.name, definition)
                } else null
            } else null
        }
// zhangxin 版本
//    private fun PsiElement.getContexts(
//        psiFile: PsiFile,
//        totalRange: TextRange,
//        allPsiElements: ArrayList<PsiElement>
//    ): List<Pair<String, String>> =
//        references.mapNotNull { reference ->
//            reference.takeUnless { it.isSoft }?.resolve()?.takeIfNotDuplicate(allPsiElements)?.let { resolvedElement ->
//                val range = totalRange.takeIf { resolvedElement.insideFile(psiFile) }
//                resolvedElement.elementType.run {
//                    when {
//                        isClass() -> resolvedElement.getClassSummary(range)
//                        isFunction() -> resolvedElement.getFunctionSignature(range)
//                        else -> resolvedElement.takeTextIfNotInsideRange(range)
//                    }
//                }?.letIfNotBlank { Pair(resolvedElement.containingFile.name, it) }
//            }
//        }


    private fun List<Pair<String, String>>.estimateTokensNumber(): Int =
        fold(0) { prev, cur -> prev + RaccoonTokenUtils.estimateTokensNumber(cur.first + cur.second) }

    fun findAllContextsLocally(
        psiFile: PsiFile, maxTokens: Int, startOffset: Int, endOffset: Int
    ): List<Pair<String, String>> {
        var curTokens = 0
        val allPsiElements = ArrayList<PsiElement>()
        val result = ArrayList<Pair<String, String>>()
        val totalRange = TextRange.create(startOffset, endOffset)
        val queue = LinkedList(getAllTopLevelElements(psiFile, totalRange))
        while (queue.isNotEmpty()) {
            val cur = queue.poll() ?: break
            val contexts = cur.getContexts(psiFile, totalRange, allPsiElements)
            if (contexts.isNotEmpty()) {
                val contextsTokens = contexts.estimateTokensNumber()
                if (curTokens + contextsTokens <= maxTokens) {
                    result.addAll(contexts)
                    curTokens += contextsTokens
                    if (curTokens > (maxTokens - 20)) {
                        break
                    }
                }
            }
            queue.addAll(cur.children)
        }
        return result
    }
}