package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BehaviorCommonHeader(
    @SerialName("client_agent")
    val clientAgent: String = RaccoonUtils.userAgent,
    @SerialName("machine_id")
    val machineId: String = RaccoonUtils.machineID ?: RaccoonUtils.DEFAULT_MACHINE_ID
)

@Serializable
sealed class UsageMetric

@Serializable
data class CommitMessageUsages(
    @SerialName("usage_num")
    var usageNumber: Int = 0
)

@Serializable
@SerialName("commit-message")
data class CommitMessageMetric(
    @SerialName("commit_message")
    val commitMessage: CommitMessageUsages = CommitMessageUsages()
) : UsageMetric()

@Serializable
data class CodeCompletionAcceptUsage(
    @SerialName("code_accept_num")
    var acceptNumber: Int = 0,
    @SerialName("code_generate_num")
    var generateNumber: Int = 0
)

@Serializable
data class CodeCompletionAcceptUsagesMap(
    @SerialName("metrics_by_language")
    val metricsMap: MutableMap<String, CodeCompletionAcceptUsage> = mutableMapOf()
)

@Serializable
data class CodeCompletionUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: CodeCompletionAcceptUsagesMap = CodeCompletionAcceptUsagesMap()
)

@Serializable
@SerialName("code-completion")
data class CodeCompletionMetric(
    @SerialName("code_completion")
    val codeCompletionUsages: CodeCompletionUsages = CodeCompletionUsages()
) : UsageMetric()

@Serializable
data class DialogCodeAcceptUsage(
    @SerialName("code_copy_num")
    var copyNumber: Int = 0,
    @SerialName("code_insert_num")
    var insertNumber: Int = 0,
    @SerialName("code_generate_num")
    var generateNumber: Int = 0
)

@Serializable
data class DialogCodeAcceptUsagesMap(
    @SerialName("metrics_by_language")
    val metricsMap: MutableMap<String, DialogCodeAcceptUsage> = mutableMapOf()
)

@Serializable
data class DialogWindowUsage(
    @SerialName("new_session_num")
    var sessionNumber: Int = 0,
    @SerialName("user_question_num")
    var questionNumber: Int = 0,
    @SerialName("model_answer_num")
    var answerNumber: Int = 0,
    @SerialName("regenerate_answer_num")
    var regenerateNumber: Int = 0
)

@Serializable
data class DialogUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: DialogCodeAcceptUsagesMap = DialogCodeAcceptUsagesMap(),
    @SerialName("dialog_window_usage")
    val windowUsages: DialogWindowUsage = DialogWindowUsage()
)

@Serializable
@SerialName("dialog")
data class DialogMetric(
    @SerialName("dialog")
    val dialogUsages: DialogUsages = DialogUsages()
) : UsageMetric()


private val BehaviorMetricsJson = Json {
    encodeDefaults = true
    classDiscriminator = "metric_type"
}

@Serializable
data class BehaviorMetrics(
    @SerialName("common_header")
    val header: BehaviorCommonHeader = BehaviorCommonHeader()
) {
    private val metrics: List<UsageMetric> = listOf(CommitMessageMetric(), CodeCompletionMetric(), DialogMetric())
    val commitMessageMetric: CommitMessageMetric
        get() = metrics[0] as CommitMessageMetric
    val codeCompletionMetric: CodeCompletionMetric
        get() = metrics[1] as CodeCompletionMetric
    val dialogMetric: DialogMetric
        get() = metrics[2] as DialogMetric

    fun toJsonString(): String = BehaviorMetricsJson.encodeToString(serializer(), this)
}
