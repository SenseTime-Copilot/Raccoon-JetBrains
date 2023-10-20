package com.sensetime.sensecore.sensecodeplugin.clients.models

import com.sensetime.sensecore.sensecodeplugin.actions.task.*
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.ChatConversation

object PenroseModels {
    @JvmStatic
    private fun makeDefaultDisplayText(): ModelConfig.DisplayText =
        ModelConfig.DisplayText("{${ChatConversation.Message.RAW}}")

    @JvmStatic
    private fun makePromptTemplate(
        userPrompt: ModelConfig.DisplayText,
        systemPrompt: ModelConfig.DisplayText?
    ): ModelConfig.PromptTemplate = ModelConfig.PromptTemplate(
        "user", userPrompt,
        "assistant", makeDefaultDisplayText(),
        "system", systemPrompt
    )

    @JvmStatic
    private fun makeModelConfig(
        name: String, stop: String, maxInputTokens: Int, tokenLimit: Int,
        promptTemplates: Map<String, ModelConfig.PromptTemplate>,
        defaultPromptTemplate: ModelConfig.PromptTemplate,
        maxNewTokens: Int?
    ): ModelConfig = ModelConfig(
        name, 0.5f, stop, maxInputTokens, tokenLimit,
        promptTemplates, defaultPromptTemplate,
        mapOf(
            ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
            ModelConfig.CompletionPreference.BALANCED to 256,
            ModelConfig.CompletionPreference.BEST_EFFORT to (tokenLimit - maxInputTokens)
        ), maxNewTokens
    )

    @JvmStatic
    private fun makeModelSCodeTaskPrompt(
        taskType: String,
        systemPrompt: ModelConfig.DisplayText? = null,
        custom: String = ""
    ): ModelConfig.PromptTemplate = makePromptTemplate(
        ModelConfig.DisplayText(
            "\n### Instruction:\nTask type: ${taskType}. ${
                SenseCodeBundle.message("completions.task.prompt.penrose.explanation")
            }.${custom}\n\n### Input:\n{code}\n", "### $taskType\n${custom}\n\n### Code:\n{code}\n"
        ), systemPrompt
    )

    @JvmStatic
    fun makeModelSConfig(
        name: String,
        systemPrompt: ModelConfig.DisplayText? = null,
        stop: String = "<|end|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        promptTemplates: Map<String, ModelConfig.PromptTemplate> = mapOf(
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
            ),
            ClientConfig.INLINE_MIDDLE to makePromptTemplate(
                ModelConfig.DisplayText("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_suffix>{suffix}<fim_middle>"),
                systemPrompt
            ),
            ClientConfig.INLINE_END to makePromptTemplate(
                ModelConfig.DisplayText("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\\n\\n{prefix}<fim_middle><fim_suffix>"),
                systemPrompt
            )
        ),
        defaultPromptTemplate: ModelConfig.PromptTemplate = makePromptTemplate(makeDefaultDisplayText(), systemPrompt),
        maxNewTokens: Int? = null
    ): ModelConfig = makeModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        promptTemplates, defaultPromptTemplate, maxNewTokens
    )

    @JvmStatic
    fun makeModelLConfig(
        name: String,
        systemPrompt: ModelConfig.DisplayText? = null,
        stop: String = "<|endofmessage|>",
        maxInputTokens: Int = 4096,
        tokenLimit: Int = 8192,
        promptTemplates: Map<String, ModelConfig.PromptTemplate> = emptyMap(),
        defaultPromptTemplate: ModelConfig.PromptTemplate = makePromptTemplate(makeDefaultDisplayText(), systemPrompt),
        maxNewTokens: Int? = null
    ): ModelConfig = makeModelConfig(
        name, stop, maxInputTokens, tokenLimit,
        promptTemplates, defaultPromptTemplate, maxNewTokens
    )
}