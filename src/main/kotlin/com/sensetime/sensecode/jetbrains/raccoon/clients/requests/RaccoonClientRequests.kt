package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


// BehaviorMetrics

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class BehaviorMetrics(
    @SerialName("common_header")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val header: CommonHeader = commonHeader
) {
    // inner classes

    @Serializable
    data class CommonHeader(
        @SerialName("client_agent") val clientAgent: String,
        @SerialName("machine_id") val machineId: String
    )

    @Serializable
    sealed interface BehaviorMetric

    @Serializable
    data class CommitMessageUsages(@SerialName("usage_num") var usageNumber: Int = 0)

    @Serializable
    @SerialName("commit-message")
    data class CommitMessageMetric(
        @SerialName("commit_message")
        val commitMessageUsages: CommitMessageUsages = CommitMessageUsages()
    ) : BehaviorMetric

    @Serializable
    data class LanguageUsagesMap<R>(
        @SerialName("metrics_by_language")
        val languageUsagesMap: MutableMap<String, R> = mutableMapOf()
    )

    @Serializable
    data class CodeCompletionAcceptUsages(
        @SerialName("code_accept_num") var acceptNumber: Int = 0,
        @SerialName("code_generate_num") var generateNumber: Int = 0
    )

    @Serializable
    data class CodeCompletionUsages(
        @SerialName("code_accept_usage")
        val acceptLanguageUsages: LanguageUsagesMap<CodeCompletionAcceptUsages> = LanguageUsagesMap()
    )

    @Serializable
    @SerialName("code-completion")
    data class CodeCompletionMetric(
        @SerialName("code_completion")
        val codeCompletionUsages: CodeCompletionUsages = CodeCompletionUsages()
    ) : BehaviorMetric

    @Serializable
    data class DialogCodeAcceptUsages(
        @SerialName("code_copy_num") var copyNumber: Int = 0,
        @SerialName("code_insert_num") var insertNumber: Int = 0,
        @SerialName("code_generate_num") var generateNumber: Int = 0
    )

    @Serializable
    data class DialogWindowUsages(
        @SerialName("new_session_num") var sessionNumber: Int = 0,
        @SerialName("user_question_num") var questionNumber: Int = 0,
        @SerialName("model_answer_num") var answerNumber: Int = 0,
        @SerialName("regenerate_answer_num") var regenerateNumber: Int = 0
    )

    @Serializable
    data class DialogUsages(
        @SerialName("code_accept_usage")
        val acceptLanguageUsages: LanguageUsagesMap<DialogCodeAcceptUsages> = LanguageUsagesMap(),
        @SerialName("dialog_window_usage")
        val windowUsages: DialogWindowUsages = DialogWindowUsages()
    )

    @Serializable
    @SerialName("dialog")
    data class DialogMetric(
        @SerialName("dialog")
        val dialogUsages: DialogUsages = DialogUsages()
    ) : BehaviorMetric


    // metrics

    private val metrics: MutableList<BehaviorMetric> = mutableListOf()
    val commitMessageMetric: CommitMessageMetric by lazy(LazyThreadSafetyMode.NONE) {
        CommitMessageMetric().also { metrics.add(it) }
    }
    val codeCompletionMetric: CodeCompletionMetric by lazy(LazyThreadSafetyMode.NONE) {
        CodeCompletionMetric().also { metrics.add(it) }
    }
    val dialogMetric: DialogMetric by lazy(LazyThreadSafetyMode.NONE) {
        DialogMetric().also { metrics.add(it) }
    }

    fun isEmpty(): Boolean = metrics.isEmpty()
    fun toJsonString(): String = behaviorMetricsJson.encodeToString(serializer(), this)

    companion object {
        private val commonHeader: CommonHeader = CommonHeader(RaccoonUtils.userAgent, RaccoonUtils.machineID)
        private val behaviorMetricsJson = Json {
            encodeDefaults = false
            classDiscriminator = "metric_type"
        }
    }
}
