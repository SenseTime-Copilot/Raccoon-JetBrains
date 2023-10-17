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

    data class PromptTemplate(
        val prompt: String,
        val display: String? = null,
        val system: String? = null
    ) {
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

        fun getPromptContent(args: Map<String, String>? = null): String = getContent(prompt, args)
        fun getDisplayText(args: Map<String, String>? = null): String = getContent(display ?: prompt, args)
        fun getSystemContent(args: Map<String, String>? = null): String? = system?.let { getContent(it, args) }
    }

    fun getMaxNewTokens(completionPreference: CompletionPreference): Int =
        maxNewTokens ?: completionPreferenceMap.getValue(completionPreference)
}
