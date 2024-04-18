package com.sensetime.sensecode.jetbrains.raccoon.llm.prompts

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank


private val LOG = logger<PromptUtils>()

internal fun String.toVariableExpression(): String = "{${trim()}}"

internal fun String.replaceVariables(variables: Map<String, String>?): String =
    Regex("""\{([\w ]*)\}""").replace(this) { matchResult ->
        matchResult.groupValues.getOrNull(1)?.trim()?.letIfNotBlank { variableName -> variables?.get(variableName) }
            ?: "".also { LOG.warn("template($this), match groups(${matchResult.groupValues}), but not found in variables($variables)") }
    }.also { result -> LOG.trace { "template($this) and variables($variables) -> \"$result\"" } }


internal object PromptVariables {
    const val TEXT = "text"
    const val CODE = "code"
    const val DIFF = "diff"
    const val LANGUAGE = "language"

    const val PREFIX_LINES = "prefixLines"
    const val SUFFIX_LINES = "suffixLines"
    const val PREFIX_CURSOR = "prefixCursor"
    const val SUFFIX_CURSOR = "suffixCursor"


    @JvmField
    val textExpression: String = TEXT.toVariableExpression()

    @JvmField
    val codeExpression: String = CODE.toVariableExpression()

    @JvmField
    val diffExpression: String = DIFF.toVariableExpression()

    @JvmField
    val languageExpression: String = LANGUAGE.toVariableExpression()

    @JvmField
    val markdownCodeTemplate: String = "```${languageExpression}\n${codeExpression}\n```"

    @JvmField
    val prefixLinesExpression: String = PREFIX_LINES.toVariableExpression()

    @JvmField
    val suffixLinesExpression: String = SUFFIX_LINES.toVariableExpression()

    @JvmField
    val prefixCursorExpression: String = PREFIX_CURSOR.toVariableExpression()

    @JvmField
    val suffixCursorExpression: String = SUFFIX_CURSOR.toVariableExpression()
}


internal object PromptUtils
