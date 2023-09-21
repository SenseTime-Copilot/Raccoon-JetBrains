package com.sensetime.sensecore.sensecodeplugin.clients

import com.sensetime.sensecore.sensecodeplugin.actions.task.*
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.i18n.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import okhttp3.Request

class SenseCoreClient : AMSCodeClient() {
    override val name: String = CLIENT_NAME

    override fun addAuthorizationToHeader(requestBuilder: Request.Builder, request: CodeRequest): Request.Builder {
        return requestBuilder
    }

    companion object {
        private const val TOKEN_LIMIT_L = 4096
        private const val TOKEN_LIMIT_411 = 4096
        private const val CLIENT_NAME = "sensecore"

        @JvmStatic
        private fun getPrompt(taskType: String, custom: String = ""): String =
            "\n### Instruction:\nTask type: ${taskType}. ${SenseCodeBundle.message("completions.task.prompt.penrose.explanation")}.${custom}\n\n### Input:\n{code}\n"

        @JvmStatic
        private fun makePenroseModelConfig(
            name: String,
            stop: String,
            tokenLimit: Int,
            codeTaskActions: Map<String, ModelConfig.PromptTemplate>,
            freeChatPromptTemplate: ModelConfig.PromptTemplate,
            customPromptTemplate: Map<String, ModelConfig.PromptTemplate>,
            inlineCompletionPromptTemplate: Map<String, ModelConfig.PromptTemplate>,
        ): ModelConfig = ModelConfig(
            name,
            0.5f,
            stop,
            tokenLimit,
            codeTaskActions,
            freeChatPromptTemplate,
            customPromptTemplate,
            inlineCompletionPromptTemplate,
            mapOf(
                ModelConfig.CompletionPreference.SPEED_PRIORITY to 128,
                ModelConfig.CompletionPreference.BALANCED to 256,
                ModelConfig.CompletionPreference.BEST_EFFORT to tokenLimit
            )
        )

        @JvmStatic
        private fun makePenrose411ModelConfig() = makePenroseModelConfig(
            "penrose-411",
            "<|end|>",
            TOKEN_LIMIT_411,
            mapOf(
                CodeTaskActionBase.getActionKey(GenerationAction::class) to ModelConfig.PromptTemplate(
                    getPrompt("code generation")
                ),
                CodeTaskActionBase.getActionKey(AddTestAction::class) to ModelConfig.PromptTemplate(
                    getPrompt("test sample generation")
                ),
                CodeTaskActionBase.getActionKey(CodeConversionAction::class) to ModelConfig.PromptTemplate(
                    getPrompt(
                        "code language conversion",
                        SenseCodeBundle.message("completions.task.prompt.penrose.language.convert")
                    )
                ),
                CodeTaskActionBase.getActionKey(CodeCorrectionAction::class) to ModelConfig.PromptTemplate(
                    getPrompt("code error correction")
                ),
                CodeTaskActionBase.getActionKey(RefactoringAction::class) to ModelConfig.PromptTemplate(
                    getPrompt("code refactoring and optimization")
                )
            ),
            ModelConfig.PromptTemplate("{content}"),
            mapOf(),
            mapOf(
                "middle" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_suffix>{suffix}<fim_middle>"),
                "end" to ModelConfig.PromptTemplate("<fim_prefix>Please do not provide any explanations at the end. Please complete the following code.\n\n{prefix}<fim_middle><fim_suffix>")
            )
        )

        @JvmStatic
        private fun makePenroseLModelConfig() = makePenroseModelConfig(
            "penrose-l",
            "<|endofmessage|>",
            TOKEN_LIMIT_L,
            mapOf(),
            ModelConfig.PromptTemplate("{content}"),
            mapOf(),
            mapOf()
        )

        @JvmStatic
        fun getDefaultClientConfig(): ClientConfig = ClientConfig(
            CLIENT_NAME,
            ::SenseCoreClient,
            "https://ams.sensecoreapi.cn/studio/ams/data/v1/chat/completions",
            listOf(makePenrose411ModelConfig(), makePenroseLModelConfig()),
            0,
            1,
            0,
            0
        )
    }
}