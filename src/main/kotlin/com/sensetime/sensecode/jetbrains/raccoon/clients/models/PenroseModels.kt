package com.sensetime.sensecode.jetbrains.raccoon.clients.models

import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.tasks.*

object PenroseModels {
    @JvmStatic
    private fun createModelConfig(
        name: String, stop: String, maxInputTokens: Int, tokenLimit: Int,
        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate>,
        systemPrompt: String?,
        roleMap: Map<ModelConfig.Role, String>?
    ): ModelConfig = ModelConfig(
        name, 0.5f, stop, maxInputTokens, tokenLimit,
        mapOf(
            ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
            ModelConfig.CompletionPreference.BALANCED to 256,
            ModelConfig.CompletionPreference.BEST_EFFORT to (tokenLimit - maxInputTokens)
        ), promptTemplates, systemPrompt, roleMap
    )

    @JvmStatic
    private fun createModelSCodeTaskPrompt(taskType: String, custom: String = ""): ModelConfig.DisplayTextTemplate =
        ModelConfig.DisplayTextTemplate(
            "\n### Instruction:\nTask type: ${taskType}. ${RaccoonBundle.message("completions.task.prompt.penrose.explanation")}.${custom}\n\n### Input:\n${ModelConfig.DisplayTextTemplate.markdownCodeTemplate}\n",
            "### $taskType\n${custom}\n${ModelConfig.DisplayTextTemplate.markdownCodeTemplate}\n"
        )

    @JvmStatic
    fun createModelCompletionSConfig(
        name: String,
        stop: String = "<|EOT|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate> = emptyMap(),
        systemPrompt: String? = null,
        roleMap: Map<ModelConfig.Role, String>? = null
    ): ModelConfig = createModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        mapOf(ModelConfig.INLINE_COMPLETION to ModelConfig.DisplayTextTemplate("<LANG>${ModelConfig.DisplayTextTemplate.languageExpression}<SUF>${ModelConfig.DisplayTextTemplate.suffixLinesExpression}<PRE>${ModelConfig.DisplayTextTemplate.prefixLinesExpression}<MID>${ModelConfig.DisplayTextTemplate.prefixCursorExpression}")) + promptTemplates,
        systemPrompt, roleMap
    )

    @JvmStatic
    fun createModelChatLConfig(
        name: String,
        stop: String = "<|endofmessage|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate> = emptyMap(),
        systemPrompt: String? = null,
        roleMap: Map<ModelConfig.Role, String>? = null
    ): ModelConfig = createModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        mapOf(
            ModelConfig.FREE_CHAT to ModelConfig.DisplayTextTemplate(ModelConfig.DisplayTextTemplate.textExpression),
            CodeTaskActionBase.getActionKey(Generation::class) to createModelSCodeTaskPrompt("code generation"),
            CodeTaskActionBase.getActionKey(AddTest::class) to createModelSCodeTaskPrompt("test sample generation"),
            CodeTaskActionBase.getActionKey(CodeConversion::class) to createModelSCodeTaskPrompt(
                "code language conversion",
                RaccoonBundle.message(
                    "completions.task.prompt.penrose.language.convert",
                    CodeConversion.dstLanguageExpression
                )
            ),
            CodeTaskActionBase.getActionKey(CodeCorrection::class) to createModelSCodeTaskPrompt("code error correction"),
            CodeTaskActionBase.getActionKey(Refactoring::class) to createModelSCodeTaskPrompt("code refactoring and optimization")
        ) + promptTemplates, systemPrompt, roleMap
    )
}