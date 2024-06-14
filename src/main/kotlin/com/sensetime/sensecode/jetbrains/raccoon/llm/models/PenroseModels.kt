package com.sensetime.sensecode.jetbrains.raccoon.llm.models

import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMMessage
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.DisplayTextTemplate
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.PromptVariables
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.AgentModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.tasks.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.math.min


@Serializable
internal data class PenroseCompletionModelConfig(
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
    override val roleMap: Map<LLMMessage.Role, String>? = null,
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
internal data class PenroseChatModelConfig(
    override val name: String = "SenseChat-Code",
    override val temperature: Float = 0.4f,
    override val stop: List<String> = listOf("<|endofmessage|>"),
    override val maxInputTokens: Int = 28672,
    override val tokenLimit: Int = 32768,
    override val promptTemplates: Map<String, DisplayTextTemplate> = DEFAULT_PROMPT_TEMPLATES,
    override val roleMap: Map<LLMMessage.Role, String>? = null,
    override val systemPrompt: String? = null,
    override val customRequestArgs: JsonObject? = null
) : ChatModelConfig() {

    companion object {
        private val lang = RaccoonConfig.config.lang

        object LanguageService {

            private val messages: Map<String, Map<String, String>> = mapOf(
                "en" to mapOf(
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text" to "Add Test",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text" to "Code Conversion",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text" to "Code Correction",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text" to "Generation",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text" to "Refactoring",
                    "completions.task.prompt.penrose.AddTest" to "Generate unit test code to the following code.",
                    "completions.task.prompt.penrose.CodeConversion" to "Convert the given code to equivalent {0} code.",
                    "completions.task.prompt.penrose.CodeCorrection" to "Fix any problem in the following code.",
                    "completions.task.prompt.penrose.Generation" to "Generate code according comment message.",
                    "completions.task.prompt.penrose.Refactoring" to "Refactor the following code to make its structure clearer, easier to read, and maintain",
                ),
                "zh" to mapOf(
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text" to "添加测试",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text" to "代码转换",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text" to "代码修正",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text" to "注释生成代码",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text" to "代码重构",
                    "completions.task.prompt.penrose.AddTest" to "为以下代码生成测试用例代码.",
                    "completions.task.prompt.penrose.CodeConversion" to "将以下代码改写为等价的 {0} 代码.",
                    "completions.task.prompt.penrose.CodeCorrection" to "修正以下代码中的问题.",
                    "completions.task.prompt.penrose.Generation" to "根据注释生成相应的代码.",
                    "completions.task.prompt.penrose.Refactoring" to "重构以下代码, 使其结构更加清晰, 更易于阅读和维护",
                ),
                "hant" to mapOf(
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text" to "測試代碼生成",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text" to "代碼翻譯",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text" to "代碼修正",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text" to "註釋生成代碼",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text" to "代碼重構",
                    "completions.task.prompt.penrose.AddTest" to "為以下代碼生成測試用例代碼.",
                    "completions.task.prompt.penrose.CodeConversion" to "將以下代碼改寫為等價的 {0} 代碼.",
                    "completions.task.prompt.penrose.CodeCorrection" to "修正以下代碼中的問題.",
                    "completions.task.prompt.penrose.Generation" to "根據註釋生成相應的代碼.",
                    "completions.task.prompt.penrose.Refactoring" to "重構以下代碼, 使其結構更加清晰, 更易於閱讀和維護",
                    ),
                "ja" to mapOf(
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text" to "テストコードを生成",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text" to "コード変換",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text" to "コード修正",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text" to "コードを生成",
                    "action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text" to "コード再構築",
                    "completions.task.prompt.penrose.AddTest" to "以下のコードに対して、ユニットテストコードを生成",
                    "completions.task.prompt.penrose.CodeConversion" to "以下のコードを同様な {0} コードに変換",
                    "completions.task.prompt.penrose.CodeCorrection" to "以下のコードの問題を修正",
                    "completions.task.prompt.penrose.Generation" to "コメントメッセージでコードを生成",
                    "completions.task.prompt.penrose.Refactoring" to "次のコードを再構築して、構造をより明確にし、読みやすくメンテナンスしやすくする",
                )
            )

            fun getInfo(language: String, actionKey: String, params: String = ""): String {
                return messages[language.lowercase()]?.get(actionKey)?.replace("{0}", params) ?: "Text not available"
            }
        }


        private val DEFAULT_PROMPT_TEMPLATES = if (lang == "Auto") mapOf(
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
        ) else mapOf(
            FREE_CHAT to DisplayTextTemplate(PromptVariables.textExpression),
            CodeTaskActionBase.getActionKey(Generation::class) to createCodeTaskPromptTemplate(
                LanguageService.getInfo(lang,"action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Generation.text"),
                LanguageService.getInfo(lang,"completions.task.prompt.penrose.Generation")
            ),
            CodeTaskActionBase.getActionKey(AddTest::class) to createCodeTaskPromptTemplate(
                LanguageService.getInfo(lang,"action.com.sensetime.sensecode.jetbrains.raccoon.tasks.AddTest.text"),
                LanguageService.getInfo(lang,"completions.task.prompt.penrose.AddTest")
            ),
            CodeTaskActionBase.getActionKey(CodeConversion::class) to createCodeTaskPromptTemplate(
                LanguageService.getInfo(lang,"action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeConversion.text"),
                LanguageService.getInfo(lang,
                    "completions.task.prompt.penrose.CodeConversion",
                    CodeConversion.dstLanguageExpression
                )
            ),
            CodeTaskActionBase.getActionKey(CodeCorrection::class) to createCodeTaskPromptTemplate(
                LanguageService.getInfo(lang,"action.com.sensetime.sensecode.jetbrains.raccoon.tasks.CodeCorrection.text"),
                LanguageService.getInfo(lang,"completions.task.prompt.penrose.CodeCorrection")
            ),
            CodeTaskActionBase.getActionKey(Refactoring::class) to createCodeTaskPromptTemplate(
                LanguageService.getInfo(lang,"action.com.sensetime.sensecode.jetbrains.raccoon.tasks.Refactoring.text"),
                LanguageService.getInfo(lang,"completions.task.prompt.penrose.Refactoring")
            )
        )

        @JvmStatic
        private fun createCodeTaskPromptTemplate(taskType: String, text: String): DisplayTextTemplate =
            PromptVariables.markdownCodeTemplate.let {
                DisplayTextTemplate("$text\n\n$it", "### $taskType\n$text\n$it")
            }
    }
}

@Serializable
internal data class PenroseAgentModelConfig(
    override val name: String = "SenseChat-Code",
    override val temperature: Float = 0.4f,
    override val stop: List<String> = listOf("<|endofmessage|>"),
    override val maxInputTokens: Int = 6144,
    override val tokenLimit: Int = 8192,
    override val tools: JsonArray = JsonArray(emptyList()),
    override val roleMap: Map<LLMMessage.Role, String>? = null,
    override val systemPrompt: String? = null,
    override val customRequestArgs: JsonObject? = null
) : AgentModelConfig()
