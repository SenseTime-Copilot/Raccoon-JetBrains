package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BehaviorCommonHeader(
    @SerialName("client_agent")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val clientAgent: String = RaccoonUtils.userAgent,
    @SerialName("machine_id")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val machineId: String = RaccoonUtils.machineID ?: RaccoonUtils.DEFAULT_MACHINE_ID
)

@Serializable
sealed class UsageMetric {
    abstract fun isEmpty(): Boolean
}

@Serializable
data class CommitMessageUsages(
    @SerialName("usage_num")
    var usageNumber: Int = 0
) {
    fun isEmpty(): Boolean = (usageNumber <= 0)
}

@Serializable
@SerialName("commit-message")
data class CommitMessageMetric(
    @SerialName("commit_message")
    val commitMessage: CommitMessageUsages = CommitMessageUsages()
) : UsageMetric() {
    override fun isEmpty(): Boolean = commitMessage.isEmpty()
}

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
) {
    fun isEmpty(): Boolean = metricsMap.isEmpty()
}

@Serializable
data class CodeCompletionUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: CodeCompletionAcceptUsagesMap = CodeCompletionAcceptUsagesMap()
) {
    fun isEmpty(): Boolean = acceptUsagesMap.isEmpty()
}

@Serializable
@SerialName("code-completion")
data class CodeCompletionMetric(
    @SerialName("code_completion")
    val codeCompletionUsages: CodeCompletionUsages = CodeCompletionUsages()
) : UsageMetric() {
    override fun isEmpty(): Boolean = codeCompletionUsages.isEmpty()
}

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
) {
    fun isEmpty(): Boolean = metricsMap.isEmpty()
}

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
) {
    fun isEmpty(): Boolean =
        ((sessionNumber <= 0) && (questionNumber <= 0) && (answerNumber <= 0) && (regenerateNumber <= 0))
}

@Serializable
data class DialogUsages(
    @SerialName("code_accept_usage")
    val acceptUsagesMap: DialogCodeAcceptUsagesMap = DialogCodeAcceptUsagesMap(),
    @SerialName("dialog_window_usage")
    val windowUsages: DialogWindowUsage = DialogWindowUsage()
) {
    fun isEmpty(): Boolean = acceptUsagesMap.isEmpty() && windowUsages.isEmpty()
}

@Serializable
@SerialName("dialog")
data class DialogMetric(
    @SerialName("dialog")
    val dialogUsages: DialogUsages = DialogUsages()
) : UsageMetric() {
    override fun isEmpty(): Boolean = dialogUsages.isEmpty()
}


private val BehaviorMetricsJson = Json {
    encodeDefaults = false
    classDiscriminator = "metric_type"
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BehaviorMetrics(
    @SerialName("common_header")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val header: BehaviorCommonHeader = BehaviorCommonHeader()
) {
    private var metrics: List<UsageMetric> = listOf(CommitMessageMetric(), CodeCompletionMetric(), DialogMetric())
    val commitMessageMetric: CommitMessageMetric
        get() = metrics[0] as CommitMessageMetric
    val codeCompletionMetric: CodeCompletionMetric
        get() = metrics[1] as CodeCompletionMetric
    val dialogMetric: DialogMetric
        get() = metrics[2] as DialogMetric

    private fun filterIfEmpty(): BehaviorMetrics {
        metrics = metrics.filterNot { it.isEmpty() }
        return this
    }

    fun toJsonString(): String = BehaviorMetricsJson.encodeToString(serializer(), this.filterIfEmpty())
}
