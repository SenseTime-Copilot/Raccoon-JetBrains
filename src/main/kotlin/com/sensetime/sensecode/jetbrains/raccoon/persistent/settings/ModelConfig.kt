package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.DisplayTextTemplate
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.replaceVariables
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.min


@Serializable
abstract class ModelConfig {
    enum class Role(val defaultName: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool")
    }

    abstract val name: String
    abstract val temperature: Float
    abstract val stop: List<String>
    abstract val maxInputTokens: Int
    abstract val tokenLimit: Int
    protected abstract val roleMap: Map<Role, String>?
    protected abstract val systemPrompt: String?
    abstract val customRequestArgs: JsonObject?

    @SerialName("maxNewTokens")
    private val _maxNewTokens: Int? = null
    private fun getNewTokenLimit(): Int = tokenLimit - maxInputTokens
    protected open fun getDefaultMaxNewTokens(): Int = getNewTokenLimit()
    fun getMaxNewTokens(): Int = min((_maxNewTokens?.takeIf { it > 0 } ?: getDefaultMaxNewTokens()), getNewTokenLimit())

    fun getRoleString(role: Role): String = (roleMap?.get(role)) ?: role.defaultName
//    fun getLLMSystemMessage(): LLMSystemMessage? = systemPrompt?.let { LLMSystemMessage(it) }
}

@Serializable
abstract class CompletionModelConfig : ModelConfig() {
    enum class CompletionPreference(val key: String) {
        SPEED_PRIORITY("settings.CompletionPreference.SingleLine"),
        BALANCED("settings.CompletionPreference.Balanced"),
        BEST_EFFORT("settings.CompletionPreference.BestEffort")
    }

    protected abstract val promptTemplate: String
    protected abstract val completionPreferenceMap: Map<CompletionPreference, Int>

    override fun getDefaultMaxNewTokens(): Int =
        completionPreferenceMap.getValue(RaccoonSettingsState.instance.inlineCompletionPreference)

    fun getPrompt(variables: Map<String, String>? = null) = promptTemplate.replaceVariables(variables)
}

@Serializable
abstract class ChatModelConfig : ModelConfig() {
    protected abstract val promptTemplates: Map<String, DisplayTextTemplate>
    fun getPromptTemplate(type: String): DisplayTextTemplate? = promptTemplates[type]

    companion object {
        const val FREE_CHAT = "Chat"
    }
}

@Serializable
abstract class AgentModelConfig : ModelConfig() {
    abstract val tools: JsonArray
}
