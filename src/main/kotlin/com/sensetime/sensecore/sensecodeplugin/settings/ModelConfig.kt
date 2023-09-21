package com.sensetime.sensecore.sensecodeplugin.settings

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

    data class PromptTemplate(val prompt: String = "", val system: String = "")

    fun getMaxNewTokens(completionPreference: CompletionPreference): Int =
        maxNewTokens ?: completionPreferenceMap.getValue(completionPreference)
}
