package com.sensetime.sensecode.jetbrains.raccoon.statistics

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.messages.MessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.RaccoonClientBehaviorMetrics
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_SENSITIVE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonStatisticsListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.getOrPutDefault
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random


private val LOG = logger<RaccoonStatisticsServer>()

@Service(Service.Level.APP)
class RaccoonStatisticsServer : RaccoonStatisticsListener, Disposable {
    private var cachedStatisticsCount: Int = 0
    private val maxIntervalMs = RaccoonConfig.config.raccoonStatisticsMaxIntervalMs
    private var lastUploadTimeMs: Long = RaccoonUtils.getSteadyTimestampMs()
    private var lastBehaviorMetrics: RaccoonClientBehaviorMetrics = RaccoonClientBehaviorMetrics()
    private val metricsChannel = Channel<RaccoonClientBehaviorMetrics>(Channel.UNLIMITED)
    private var statisticsMessageBusConnection: MessageBusConnection? = null

    private var timerJob: Job? = null
    private var uploadJob: Job? = null
    private var sensitiveJob: Job? = null
    private val statisticsCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("RaccoonStatisticsServer"))

    companion object {
        @JvmStatic
        fun getInstance(): RaccoonStatisticsServer = service()

        @JvmStatic
        private fun cvtLanguage(language: String): String = language.ifBlank { "Unknown" }
    }

    fun onProjectOpened() {
        ApplicationManager.getApplication().invokeLater {
            if (null == uploadJob) {
                startTask()
            }
        }
    }

    fun onProjectClosed() {
        updateBehaviorMetrics(true)
    }

    private fun startTask() {
        LOG.trace { "startTask in" }
        require(null == timerJob) { "startTask(timerJob) must run once only!" }
        require(null == uploadJob) { "startTask(uploadJob) must run once only!" }
        require(null == sensitiveJob) { "startTask(sensitiveJob) must run once only!" }
        require(null == statisticsMessageBusConnection) { "startTask(statisticsMessageBusConnection) must run once only!" }

        statisticsMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(RACCOON_STATISTICS_TOPIC, this)
        }

        timerJob = statisticsCoroutineScope.launch {
            while (true) {
                delay((maxIntervalMs * Random.nextDouble(0.8, 1.2)).toLong())
                updateBehaviorMetrics()
            }
        }

        uploadJob = statisticsCoroutineScope.launch {
            for (metrics in metricsChannel) {
                RaccoonExceptions.resultOf {
                    while (true) {
                        LOG.debug { "start uploadBehaviorMetrics" }
                        if (LLMClientManager.currentLLMClient.uploadBehaviorMetrics(metrics)) {
                            LOG.debug { "run uploadBehaviorMetrics ok" }
                            break
                        }
                        LOG.debug { "run uploadBehaviorMetrics failed" }
                        delay((maxIntervalMs * Random.nextDouble(0.8, 1.2)).toLong())
                    }
                    LOG.debug { "run uploadBehaviorMetrics finished" }
                }
            }
            LOG.debug { "uploadJob finished" }
        }

        sensitiveJob = statisticsCoroutineScope.launch {
            var lastUpdateTime: Long = RaccoonUtils.getDateTimestampMs()
            while (true) {
                RaccoonExceptions.resultOf {
                    delay((Random.nextDouble(1.5, 2.5) * 3600 * 1000).toLong())
                    val tmpTime = RaccoonUtils.getDateTimestampMs()
                    val sensitives =
                        LLMClientManager.currentLLMClient.getSensitiveConversations(
                            lastUpdateTime.toString(),
                            action = "timer"
                        )
                    lastUpdateTime = tmpTime
                    if (sensitives.isNotEmpty()) {
                        ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_SENSITIVE_TOPIC)
                            .onNewSensitiveConversations(sensitives)
                    }
                }
            }
        }
        LOG.trace { "startTask out" }
    }

    override fun dispose() {
        LOG.trace { "dispose in" }
        timerJob?.cancel()
        sensitiveJob?.cancel()
        statisticsMessageBusConnection?.disconnect()
        metricsChannel.close()
        LOG.debug { "start wait uploadJob" }
        runBlocking {
            withTimeoutOrNull(3000L) {
                uploadJob?.join()
                LOG.debug { "uploadJob stopped ok" }
            }
        }
        LOG.debug { "end wait uploadJob" }
        statisticsCoroutineScope.cancel()
        LOG.trace { "dispose out" }
    }

    private fun sendCurrentMetrics() {
        if (cachedStatisticsCount > 0) {
            metricsChannel.trySend(lastBehaviorMetrics)
            lastBehaviorMetrics = RaccoonClientBehaviorMetrics()
        }
        cachedStatisticsCount = 0
        lastUploadTimeMs = RaccoonUtils.getSteadyTimestampMs()
    }

    private fun updateBehaviorMetrics(
        forceUpload: Boolean = false,
        updateOnUIThread: ((RaccoonClientBehaviorMetrics) -> Unit)? = null
    ) {
        ApplicationManager.getApplication().invokeLater {
            updateOnUIThread?.let {
                it.invoke(lastBehaviorMetrics)
                cachedStatisticsCount += 1
            }
            if (forceUpload || (cachedStatisticsCount >= RaccoonConfig.config.raccoonStatisticsMaxCacheCount) || ((RaccoonUtils.getSteadyTimestampMs() - lastUploadTimeMs) >= maxIntervalMs)) {
                sendCurrentMetrics()
            }
        }
    }

    override fun onGenerateGitCommitMessageFinished() {
        updateBehaviorMetrics {
            it.commitMessageMetric.commitMessageUsages.usageNumber += 1
        }
    }

    override fun onInlineCompletionFinished(language: String, candidates: Int, line: Int) {
        updateBehaviorMetrics {
            it.codeCompletionMetric.codeCompletionUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.CodeCompletionAcceptUsages()
            ).generateNumber += candidates
            it.codeCompletionMetric.codeCompletionUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.CodeCompletionAcceptUsages()
            ).generateLineNumber += line
        }
    }

    override fun onInlineCompletionAccepted(language: String, line: Int) {
        updateBehaviorMetrics {
            it.codeCompletionMetric.codeCompletionUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.CodeCompletionAcceptUsages()
            ).acceptNumber += 1
            it.codeCompletionMetric.codeCompletionUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.CodeCompletionAcceptUsages()
            ).acceptLineNumber += line
        }
    }

    override fun onToolWindowNewSession() {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.windowUsages.sessionNumber += 1
        }
    }

    override fun onToolWindowQuestionSubmitted() {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.windowUsages.questionNumber += 1
        }
    }

    override fun onToolWindowAnswerFinished() {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.windowUsages.answerNumber += 1
        }
    }

    override fun onToolWindowRegenerateClicked() {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.windowUsages.regenerateNumber += 1
        }
    }

    override fun onToolWindowCodeGenerated(languages: List<String>) {
        updateBehaviorMetrics {
            for (language in languages) {
                it.dialogMetric.dialogUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                    cvtLanguage(language),
                    RaccoonClientBehaviorMetrics.DialogCodeAcceptUsages()
                ).generateNumber += 1
            }
        }
    }

    override fun onToolWindowCodeCopied(language: String) {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.DialogCodeAcceptUsages()
            ).copyNumber += 1
        }
    }

    override fun onToolWindowCodeInserted(language: String) {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.acceptLanguageUsages.languageUsagesMap.getOrPutDefault(
                cvtLanguage(language),
                RaccoonClientBehaviorMetrics.DialogCodeAcceptUsages()
            ).insertNumber += 1
        }
    }
}
