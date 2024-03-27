//package com.sensetime.sensecode.jetbrains.raccoon.clients.models
//
//import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
//import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
//import com.sensetime.sensecode.jetbrains.raccoon.tasks.*
//import kotlin.math.min
//
//object PenroseModels {
//    @JvmStatic
//    private fun createModelConfig(
//        name: String, temperature: Float, stop: String, maxInputTokens: Int, tokenLimit: Int,
//        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate>,
//        systemPrompt: String?,
//        roleMap: Map<ModelConfig.Role, String>?
//    ): ModelConfig = ModelConfig(
//        name, temperature, stop, maxInputTokens, tokenLimit,
//        mapOf(
//            ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
//            ModelConfig.CompletionPreference.BALANCED to 256,
//            ModelConfig.CompletionPreference.BEST_EFFORT to min(1024, (tokenLimit - maxInputTokens))
//        ), promptTemplates, systemPrompt, roleMap
//    )
//
//    @JvmStatic
//    private fun createModelSCodeTaskPrompt(taskType: String, text: String): ModelConfig.DisplayTextTemplate =
//        ModelConfig.DisplayTextTemplate(
//            "$text\n\n${ModelConfig.DisplayTextTemplate.markdownCodeTemplate}",
//            "### $taskType\n${text}\n${ModelConfig.DisplayTextTemplate.markdownCodeTemplate}"
//        )
//
//    @JvmStatic
//    fun createModelCompletionSConfig(
//        name: String,
//        stop: String = "<EOT>",
//        maxInputTokens: Int = 12288,
//        tokenLimit: Int = 16384,
//        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate> = emptyMap(),
//        systemPrompt: String? = null,
//        roleMap: Map<ModelConfig.Role, String>? = null
//    ): ModelConfig = createModelConfig(
//        name, 0.4f, stop, maxInputTokens, tokenLimit,
//        mapOf(ModelConfig.INLINE_COMPLETION to ModelConfig.DisplayTextTemplate("<LANG>${ModelConfig.DisplayTextTemplate.languageExpression}<SUF>${ModelConfig.DisplayTextTemplate.suffixLinesExpression}<PRE>${ModelConfig.DisplayTextTemplate.prefixLinesExpression}<COVER>${ModelConfig.DisplayTextTemplate.suffixCursorExpression}<MID>${ModelConfig.DisplayTextTemplate.prefixCursorExpression}")) + promptTemplates,
//        systemPrompt, roleMap
//    )
//
//    @JvmStatic
//    fun createModelChatLConfig(
//        name: String,
//        stop: String = "<|endofmessage|>",
//        maxInputTokens: Int = 6144,
//        tokenLimit: Int = 8192,
//        promptTemplates: Map<String, ModelConfig.DisplayTextTemplate> = emptyMap(),
//        systemPrompt: String? = null,
//        roleMap: Map<ModelConfig.Role, String>? = null
//    ): ModelConfig = createModelConfig(
//        name, 0.4f, stop, maxInputTokens, tokenLimit,
//        mapOf(
//            ModelConfig.FREE_CHAT to ModelConfig.DisplayTextTemplate(ModelConfig.DisplayTextTemplate.textExpression),
//            CodeTaskActionBase.getActionKey(Generation::class) to createModelSCodeTaskPrompt(
//                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text"),
//                RaccoonBundle.message("completions.task.prompt.penrose.Generation")
//            ),
//            CodeTaskActionBase.getActionKey(AddTest::class) to createModelSCodeTaskPrompt(
//                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text"),
//                RaccoonBundle.message("completions.task.prompt.penrose.AddTest")
//            ),
//            CodeTaskActionBase.getActionKey(CodeConversion::class) to createModelSCodeTaskPrompt(
//                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text"),
//                RaccoonBundle.message(
//                    "completions.task.prompt.penrose.CodeConversion",
//                    CodeConversion.dstLanguageExpression
//                )
//            ),
//            CodeTaskActionBase.getActionKey(CodeCorrection::class) to createModelSCodeTaskPrompt(
//                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text"),
//                RaccoonBundle.message("completions.task.prompt.penrose.CodeCorrection")
//            ),
//            CodeTaskActionBase.getActionKey(Refactoring::class) to createModelSCodeTaskPrompt(
//                RaccoonBundle.message("action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text"),
//                RaccoonBundle.message("completions.task.prompt.penrose.Refactoring")
//            )
//        ) + promptTemplates, systemPrompt, roleMap
//    )
//}