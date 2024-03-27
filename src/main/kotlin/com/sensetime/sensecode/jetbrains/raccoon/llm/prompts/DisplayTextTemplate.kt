package com.sensetime.sensecode.jetbrains.raccoon.llm.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DisplayTextTemplate(
    private val raw: String,
    @SerialName("display")
    private val _display: String? = null,
) {
    private val display: String
        get() = _display ?: raw

    fun getRawText(variables: Map<String, String>? = null): String = raw.replaceVariables(variables)
    fun getDisplayText(variables: Map<String, String>? = null): String = display.replaceVariables(variables)
}
