package com.sensetime.intellij.plugins.sensecode.persistent.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelConfig(
    val name: String,
    val temperature: Float,
    val stop: String,
    val maxInputTokens: Int,
    val tokenLimit: Int,
    private val completionPreferenceMap: Map<CompletionPreference, Int>,
    private val promptTemplates: Map<String, DisplayTextTemplate>,
    private val systemPrompt: String? = null,
    private val roleMap: Map<Role, String>? = null
) {
    enum class CompletionPreference(val key: String) {
        SPEED_PRIORITY("settings.CompletionPreference.SpeedPriority"),
        BALANCED("settings.CompletionPreference.Balanced"),
        BEST_EFFORT("settings.CompletionPreference.BestEffort")
    }

    enum class Role(val role: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system")
    }

    @Serializable
    data class DisplayTextTemplate(
        private val raw: String,
        @SerialName("display")
        private val _display: String? = null,
    ) {
        private val display: String
            get() = _display ?: raw

        fun toRawText(args: Map<String, String>? = null): String = replaceArgs(raw, args)
        fun toDisplayText(args: Map<String, String>? = null): String = replaceArgs(display, args)

        companion object {
            const val TEXT = "text"
            const val CODE = "code"
            const val LANGUAGE = "language"

            const val PREFIX_LINES = "prefixLines"
            const val SUFFIX_LINES = "suffixLines"
            const val PREFIX_CURSOR = "prefixCursor"

            val textExpression: String
                get() = toArgExpression(TEXT)
            val codeExpression: String
                get() = toArgExpression(CODE)
            val languageExpression: String
                get() = toArgExpression(LANGUAGE)

            val prefixLinesExpression: String
                get() = toArgExpression(PREFIX_LINES)
            val suffixLinesExpression: String
                get() = toArgExpression(SUFFIX_LINES)
            val prefixCursorExpression: String
                get() = toArgExpression(PREFIX_CURSOR)

            val markdownCodeTemplate: String
                get() = "```${languageExpression}\n${codeExpression}\n```"

            @JvmStatic
            fun toArgExpression(argName: String): String = "{$argName}"

            @JvmStatic
            fun replaceArgs(template: String, args: Map<String, String>?): String = args?.run {
                keys.fold(template) { preContent, currentArgName ->
                    preContent.replace(toArgExpression(currentArgName), getValue(currentArgName))
                }
            } ?: template
        }
    }

    fun getMaxNewTokens(completionPreference: CompletionPreference): Int =
        completionPreferenceMap.getValue(completionPreference)

    fun getRoleString(role: Role): String = (roleMap?.get(role)) ?: role.role

    fun getSystemPromptPair(): Pair<String, String>? = systemPrompt?.let { Pair(getRoleString(Role.SYSTEM), it) }
    fun getPromptTemplate(type: String): DisplayTextTemplate? = promptTemplates[type]

    companion object {
        const val FREE_CHAT = "Chat"
        const val INLINE_COMPLETION = "Inline"
    }
}

fun List<ModelConfig>.toMap(): Map<String, ModelConfig> = associateBy(ModelConfig::name)
