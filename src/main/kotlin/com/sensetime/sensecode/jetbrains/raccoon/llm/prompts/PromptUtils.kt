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

internal object PromptUtils
