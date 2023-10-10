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
    private fun getModelSCodeTaskPrompt(taskType: String, custom: String = ""): String =
        "\n### Instruction:\nTask type: ${taskType}. ${SenseCodeBundle.message("completions.task.prompt.penrose.explanation")}.${custom}\n\n### Input:\n%s\n"

    @JvmStatic
    fun makeModelSConfig(
        name: String,
        systemPromptTemplate: String? = null,
        stop: String = "<|end|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        codeTaskActions: Map<String, ModelConfig.PromptTemplate> = mapOf(
            CodeTaskActionBase.getActionKey(GenerationAction::class) to ModelConfig.PromptTemplate(
                getModelSCodeTaskPrompt("code generation"), systemPromptTemplate
            ),
            CodeTaskActionBase.getActionKey(AddTestAction::class) to ModelConfig.PromptTemplate(
                getModelSCodeTaskPrompt("test sample generation"), systemPromptTemplate
            ),
            CodeTaskActionBase.getActionKey(CodeConversionAction::class) to ModelConfig.PromptTemplate(
                getModelSCodeTaskPrompt(
                    "code language conversion",
                    SenseCodeBundle.message("completions.task.prompt.penrose.language.convert")
                ), systemPromptTemplate
            ),
            CodeTaskActionBase.getActionKey(CodeCorrectionAction::class) to ModelConfig.PromptTemplate(
                getModelSCodeTaskPrompt("code error correction"), systemPromptTemplate
            ),
            CodeTaskActionBase.getActionKey(RefactoringAction::class) to ModelConfig.PromptTemplate(
                getModelSCodeTaskPrompt("code refactoring and optimization"), systemPromptTemplate
            )
        ),
        freeChatPromptTemplate: ModelConfig.PromptTemplate = ModelConfig.PromptTemplate("%s%s", systemPromptTemplate),
        customPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        inlineCompletionPromptTemplate: Map<String, ModelConfig.PromptTemplate> = mapOf(
            "middle" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_suffix>{suffix}<fim_middle>"),
            "end" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_middle><fim_suffix>")
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
        systemPromptTemplate: String? = null,
        stop: String = "<|endofmessage|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        codeTaskActions: Map<String, ModelConfig.PromptTemplate> = mapOf(),
        freeChatPromptTemplate: ModelConfig.PromptTemplate = ModelConfig.PromptTemplate("%s%s", systemPromptTemplate),
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