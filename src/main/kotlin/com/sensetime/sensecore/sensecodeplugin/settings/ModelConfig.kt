package com.sensetime.sensecore.sensecodeplugin.settings

import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation.Message.Companion.RAW
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelConfig(
    val name: String,
    val temperature: Float,
    val stop: String,
    val maxInputTokens: Int,
    val tokenLimit: Int,
    val codeTaskActions: Map<String, PromptTemplate>,
    val freeChatPromptTemplate: PromptTemplate,
    val customPromptTemplate: Map<String, PromptTemplate>,
    val inlineCompletionPromptTemplate: Map<String, PromptTemplate>,
    val completionPreferenceMap: Map<CompletionPreference, Int>,
    val maxNewTokens: Int? = null
) {
    enum class CompletionPreference(val key: String) {
        SPEED_PRIORITY("settings.CompletionPreference.SpeedPriority"),
        BALANCED("settings.CompletionPreference.Balanced"),
        BEST_EFFORT("settings.CompletionPreference.BestEffort")
    }

    @Serializable
    data class DisplayText(
        val text: String,
        @SerialName("display")
        private val _display: String? = null
    ) {
        val display: String
            get() = _display ?: text
    }

    @Serializable
    data class PromptTemplate(
        val userRole: String,
        val userPrompt: DisplayText,
        val assistantRole: String,
        val assistantText: DisplayText = DisplayText("{$RAW}"),
        val systemRole: String = "system",
        val systemPrompt: DisplayText? = null
    ) {
        fun getUserPromptContent(args: Map<String, String>? = null): String = getContent(userPrompt.text, args)
        fun getUserPromptDisplay(args: Map<String, String>? = null): String = getContent(userPrompt.display, args)
        fun getAssistantTextContent(args: Map<String, String>? = null): String = getContent(assistantText.text, args)
        fun getAssistantTextDisplay(args: Map<String, String>? = null): String = getContent(assistantText.display, args)

        fun getSystemPromptContent(args: Map<String, String>? = null): String? =
            systemPrompt?.let { getContent(it.text, args) }

        fun getSystemPromptDisplay(args: Map<String, String>? = null): String? =
            systemPrompt?.let { getContent(it.display, args) }

        companion object {
            @JvmStatic
            private fun getContent(template: String, args: Map<String, String>?): String = args?.run {
                keys.fold(template) { preContent, currentArgName ->
                    preContent.replace(
                        "{$currentArgName}",
                        getValue(currentArgName)
                    )
                }
            } ?: template
        }
    }

    fun getMaxNewTokens(completionPreference: CompletionPreference): Int =
        maxNewTokens ?: completionPreferenceMap.getValue(completionPreference)
}
