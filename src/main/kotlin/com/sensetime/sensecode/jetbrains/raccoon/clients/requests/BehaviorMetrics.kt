package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BehaviorCommonHeader(
    @SerialName("client_agent")
    val clientAgent: String,
    @SerialName("machine_id")
    val machineId: String
)

@Serializable
sealed class UsageMetric

@Serializable
data class CommitMessageUsages(
    @SerialName("usage_num")
    val usageNumber: Int
)

@Serializable
@SerialName("commit-message")
data class CommitMessageMetric(
    @SerialName("commit_message")
    val commitMessage: CommitMessageUsages
) : UsageMetric()

@Serializable
data class CodeCompletionAcceptUsage(
    @SerialName("code_accept_num")
    val acceptNumber: Int,
    @SerialName("code_generate_num")
    val generateNumber: Int
)

@Serializable
data class CodeCompletionAcceptUsagesMap(
    @SerialName("metrics_by_language")
    val metricsMap: Map<String, CodeCompletionAcceptUsage>
)

@Serializable
data class CodeCompletionUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: CodeCompletionAcceptUsagesMap
)

@Serializable
@SerialName("code-completion")
data class CodeCompletionMetric(
    @SerialName("code_completion")
    val codeCompletionUsages: CodeCompletionUsages
) : UsageMetric()

@Serializable
data class DialogCodeAcceptUsage(
    @SerialName("code_copy_num")
    val copyNumber: Int,
    @SerialName("code_insert_num")
    val insertNumber: Int,
    @SerialName("code_generate_num")
    val generateNumber: Int
)

@Serializable
data class DialogCodeAcceptUsagesMap(
    @SerialName("metrics_by_language")
    val metricsMap: Map<String, DialogCodeAcceptUsage>
)

@Serializable
data class DialogWindowUsage(
    @SerialName("new_session_num")
    val sessionNumber: Int,
    @SerialName("user_question_num")
    val questionNumber: Int,
    @SerialName("model_answer_num")
    val answerNumber: Int,
    @SerialName("regenerate_answer_num")
    val regenerateNumber: Int
)

@Serializable
data class DialogUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: DialogCodeAcceptUsagesMap,
    @SerialName("dialog_window_usage")
    val windowUsages: DialogWindowUsage
)

@Serializable
@SerialName("dialog")
data class DialogMetric(
    @SerialName("dialog")
    val dialogUsages: DialogUsages
) : UsageMetric()


private val BehaviorMetricsJson = Json {
    encodeDefaults = true
    classDiscriminator = "metric_type"
}

@Serializable
data class BehaviorMetrics(
    @SerialName("common_header")
    val header: BehaviorCommonHeader,
    val metrics: List<UsageMetric>
) {
    fun toJsonString(): String = BehaviorMetricsJson.encodeToString(serializer(), this)
}
