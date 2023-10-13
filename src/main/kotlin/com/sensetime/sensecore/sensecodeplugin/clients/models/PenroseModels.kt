package com.sensetime.sensecore.sensecodeplugin.clients.models

import com.sensetime.sensecore.sensecodeplugin.actions.task.*
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig

object PenroseModels {
    @JvmStatic
    private fun makeModelConfig(
        name: String, stop: String, maxInputTokens: Int, tokenLimit: Int,
        codeTaskActions: Map<String, ModelConfig.PromptTemplate>,
        freeChatPromptTemplate: ModelConfig.PromptTemplate,
        customPromptTemplate: Map<String, ModelConfig.PromptTemplate>,
        inlineCompletionPromptTemplate: Map<String, ModelConfig.PromptTemplate>,
        maxNewTokens: Int?
    ): ModelConfig = ModelConfig(
        name, 0.5f, stop, maxInputTokens, tokenLimit,
        codeTaskActions, freeChatPromptTemplate, customPromptTemplate, inlineCompletionPromptTemplate,
        mapOf(
            ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
            ModelConfig.CompletionPreference.BALANCED to 256,
            ModelConfig.CompletionPreference.BEST_EFFORT to (tokenLimit - maxInputTokens)
        ), maxNewTokens
    )

    @JvmStatic
    private fun makeModelSCodeTaskPrompt(
        taskType: String,
        systemPrompt: String? = null,
        custom: String = ""
    ): ModelConfig.PromptTemplate = ModelConfig.PromptTemplate(
        "\n### Instruction:\nTask type: ${taskType}. ${SenseCodeBundle.message("completions.task.prompt.penrose.explanation")}.${custom}\n\n### Input:\n%s\n",
        "### $taskType\n${custom}\n\n### Code:\n%s\n",
        systemPrompt
    )

    @JvmStatic
    fun makeModelSConfig(
        name: String,
        systemPrompt: String? = null,
        stop: String = "<|end|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        codeTaskActions: Map<String, ModelConfig.PromptTemplate> = mapOf(
            CodeTaskActionBase.getActionKey(GenerationAction::class) to makeModelSCodeTaskPrompt(
                "code generation",
                systemPrompt
            ),
            CodeTaskActionBase.getActionKey(AddTestAction::class) to makeModelSCodeTaskPrompt(
                "test sample generation",
                systemPrompt
            ),
            CodeTaskActionBase.getActionKey(CodeConversionAction::class) to makeModelSCodeTaskPrompt(
                "code language conversion",
                systemPrompt,
                SenseCodeBundle.message("completions.task.prompt.penrose.language.convert")
            ),
            CodeTaskActionBase.getActionKey(CodeCorrectionAction::class) to makeModelSCodeTaskPrompt(
                "code error correction",
                systemPrompt
            ),
            CodeTaskActionBase.getActionKey(RefactoringAction::class) to makeModelSCodeTaskPrompt(
                "code refactoring and optimization",
                systemPrompt
            )
        ),
        freeChatPromptTemplate: ModelConfig.PromptTemplate = ModelConfig.PromptTemplate("%s", null, systemPrompt),
        customPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        inlineCompletionPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(
            "middle" to ModelConfig.PromptTemplate(
                "<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n%s<fim_suffix>%s<fim_middle>",
                null,
                systemPrompt
            ),
            "end" to ModelConfig.PromptTemplate(
                "<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n%s<fim_middle><fim_suffix>",
                null,
                systemPrompt
            )
        ),
        maxNewTokens: Int? = null
    ): ModelConfig = makeModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        codeTaskActions,
        freeChatPromptTemplate,
        customPromptTemplate,
        inlineCompletionPromptTemplate,
        maxNewTokens
    )

    @JvmStatic
    fun makeModelLConfig(
        name: String,
        systemPrompt: String? = null,
        stop: String = "<|endofmessage|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        codeTaskActions: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        freeChatPromptTemplate: ModelConfig.PromptTemplate = ModelConfig.PromptTemplate("%s", null, systemPrompt),
        customPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        inlineCompletionPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        maxNewTokens: Int? = null
    ): ModelConfig = makeModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        codeTaskActions,
        freeChatPromptTemplate,
        customPromptTemplate,
        inlineCompletionPromptTemplate,
        maxNewTokens
    )
}