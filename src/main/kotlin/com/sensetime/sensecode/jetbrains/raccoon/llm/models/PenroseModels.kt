package com.sensetime.sensecode.jetbrains.raccoon.llm.models

import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.DisplayTextTemplate
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.PromptVariables
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.tasks.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.math.min


@Serializable
data class PenroseCompletionModelConfig(
    override val name: String = "SenseChat-CodeCompletion-Lite",
    override val temperature: Float = 0.4f,
    override val stop: List<String> = listOf("<EOT>"),
    override val maxInputTokens: Int = 12288,
    override val tokenLimit: Int = 16384,
    override val promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    override val completionPreferenceMap: Map<CompletionPreference, Int> = mapOf(
        CompletionPreference.SPEED_PRIORITY to 128,
        CompletionPreference.BALANCED to 256,
        CompletionPreference.BEST_EFFORT to min(1024, (tokenLimit - maxInputTokens))
    ),
    override val roleMap: Map<Role, String>? = null,
    override val systemPrompt: String? = null,
    override val customRequestArgs: JsonObject? = null
) : CompletionModelConfig() {
    companion object {
        private val DEFAULT_PROMPT_TEMPLATE = PromptVariables.run {
            "<LANG>${languageExpression}<SUF>${suffixLinesExpression}<PRE>${prefixLinesExpression}<COVER>${suffixCursorExpression}<MID>${prefixCursorExpression}"
        }
    }
}

@Serializable
data class PenroseChatModelConfig(
    override val name: String = "SenseChat-Code",
    override val temperature: Float = 0.4f,
    override val stop: List<String> = listOf("<|endofmessage|>"),
    override val maxInputTokens: Int = 6144,
    override val tokenLimit: Int = 8192,
    override val promptTemplates: Map<String, DisplayTextTemplate> = DEFAULT_PROMPT_TEMPLATES,
    override val roleMap: Map<Role, String>? = null,
    override val systemPrompt: String? = null,
    override val customRequestArgs: JsonObject? = null
) : ChatModelConfig() {
    companion object {
        private val DEFAULT_PROMPT_TEMPLATES = mapOf(
            FREE_CHAT to DisplayTextTemplate(PromptVariables.textExpression),
            CodeTaskActionBase.getActionKey(Generation::class) to createCodeTaskPromptTemplate(
                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text"),
                RaccoonBundle.message("completions.task.prompt.penrose.Generation")
            ),
            CodeTaskActionBase.getActionKey(AddTest::class) to createCodeTaskPromptTemplate(
                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text"),
                RaccoonBundle.message("completions.task.prompt.penrose.AddTest")
            ),
            CodeTaskActionBase.getActionKey(CodeConversion::class) to createCodeTaskPromptTemplate(
                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text"),
                RaccoonBundle.message(
                    "completions.task.prompt.penrose.CodeConversion",
                    CodeConversion.dstLanguageExpression
                )
            ),
            CodeTaskActionBase.getActionKey(CodeCorrection::class) to createCodeTaskPromptTemplate(
                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text"),
                RaccoonBundle.message("completions.task.prompt.penrose.CodeCorrection")
            ),
            CodeTaskActionBase.getActionKey(Refactoring::class) to createCodeTaskPromptTemplate(
                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text"),
                RaccoonBundle.message("completions.task.prompt.penrose.Refactoring")
            )
        )

        @JvmStatic
        private fun createCodeTaskPromptTemplate(taskType: String, text: String): DisplayTextTemplate =
            PromptVariables.markdownCodeTemplate.let {
                DisplayTextTemplate("$text\n\n$it", "### $taskType\n$text\n$it")
            }
    }
}
