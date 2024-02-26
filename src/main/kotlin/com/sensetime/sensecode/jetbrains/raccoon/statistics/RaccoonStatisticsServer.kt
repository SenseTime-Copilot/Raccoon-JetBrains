package com.sensetime.sensecode.jetbrains.raccoon.statistics

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.messages.MessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.BehaviorMetrics
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.CodeCompletionAcceptUsage
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.DialogCodeAcceptUsage
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonStatisticsListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel


private val LOG = logger<RaccoonStatisticsServer>()

@Service(Service.Level.APP)
class RaccoonStatisticsServer : RaccoonStatisticsListener, Disposable {
    private var cachedStatisticsCount: Int = 0
    private var lastUploadTimeMs: Long = RaccoonUtils.getSteadyTimestampMs()
    private var lastBehaviorMetrics: BehaviorMetrics = BehaviorMetrics()
    private val metricsChannel = Channel<BehaviorMetrics>(Channel.UNLIMITED)
    private var statisticsMessageBusConnection: MessageBusConnection? = null

    private var timerJob: Job? = null
    private var uploadJob: Job? = null
    private val statisticsCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("RaccoonStatisticsServer"))

    companion object {
        @JvmStatic
        fun getInstance(): RaccoonStatisticsServer = service()

        private const val MAX_CACHE_COUNT: Int = 1000
        private const val MAX_INTERVAL_MS: Long = 3600L * 1000L
    }

    fun onProjectOpened() {
        RunOnceUtil.runOnceForApp(RaccoonStatisticsServer::class.qualifiedName!!, this::startTask)
    }

    fun onProjectClosed() {
        updateBehaviorMetrics(true)
    }

    private fun startTask() {
        LOG.trace { "startTask in" }
        require(null == timerJob) { "startTask(timerJob) must run once only!" }
        require(null == uploadJob) { "startTask(uploadJob) must run once only!" }
        require(null == statisticsMessageBusConnection) { "startTask(statisticsMessageBusConnection) must run once only!" }

        statisticsMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(RACCOON_STATISTICS_TOPIC, this)
        }

        timerJob = statisticsCoroutineScope.launch {
            while (true) {
                delay(MAX_INTERVAL_MS)
                updateBehaviorMetrics()
            }
        }

        uploadJob = statisticsCoroutineScope.launch {
            for (metrics in metricsChannel) {
                kotlin.runCatching {
                    while (true) {
                        LOG.debug { "start uploadBehaviorMetrics" }
                        if (RaccoonClientManager.currentCodeClient.uploadBehaviorMetrics(metrics)) {
                            LOG.debug { "run uploadBehaviorMetrics ok" }
                            break
                        }
                        LOG.debug { "run uploadBehaviorMetrics failed" }
                        delay(60000)
                    }
                    LOG.debug { "run uploadBehaviorMetrics finished" }
                }.onFailure { e ->
                    if (e is CancellationException) {
                        LOG.debug(e)
                        throw e
                    } else {
                        LOG.warn(e)
                    }
                }
            }
            LOG.debug { "uploadJob finished" }
        }
        LOG.trace { "startTask out" }
    }

    override fun dispose() {
        LOG.trace { "dispose in" }
        timerJob?.cancel()
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
        if (cachedStatisticsCount >= 0) {
            metricsChannel.trySend(lastBehaviorMetrics)
            lastBehaviorMetrics = BehaviorMetrics()
        }
        cachedStatisticsCount = 0
        lastUploadTimeMs = RaccoonUtils.getSteadyTimestampMs()
    }

    private fun updateBehaviorMetrics(
        forceUpload: Boolean = false,
        updateOnUIThread: ((BehaviorMetrics) -> Unit)? = null
    ) {
        ApplicationManager.getApplication().invokeLater {
            updateOnUIThread?.let {
                it.invoke(lastBehaviorMetrics)
                cachedStatisticsCount += 1
            }
            if (forceUpload || (cachedStatisticsCount >= MAX_CACHE_COUNT) || ((RaccoonUtils.getSteadyTimestampMs() - lastUploadTimeMs) >= MAX_INTERVAL_MS)) {
                sendCurrentMetrics()
            }
        }
    }

    override fun onGenerateGitCommitMessageFinished() {
        updateBehaviorMetrics {
            it.commitMessageMetric.commitMessage.usageNumber += 1
        }
    }

    override fun onInlineCompletionFinished(language: String) {
        updateBehaviorMetrics {
            it.codeCompletionMetric.codeCompletionUsages.acceptUsagesMap.metricsMap.getOrDefault(
                language,
                CodeCompletionAcceptUsage()
            ).generateNumber += 1
        }
    }

    override fun onInlineCompletionAccepted(language: String) {
        updateBehaviorMetrics {
            it.codeCompletionMetric.codeCompletionUsages.acceptUsagesMap.metricsMap.getOrDefault(
                language,
                CodeCompletionAcceptUsage()
            ).acceptNumber += 1
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

    override fun onToolWindowRegenerateFinished() {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.windowUsages.regenerateNumber += 1
        }
    }

    override fun onToolWindowCodeGenerated(languages: List<String>) {
        updateBehaviorMetrics {
            for (language in languages) {
                it.dialogMetric.dialogUsages.acceptUsagesMap.metricsMap.getOrDefault(
                    language,
                    DialogCodeAcceptUsage()
                ).generateNumber += 1
            }
        }
    }

    override fun onToolWindowCodeCopied(language: String) {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.acceptUsagesMap.metricsMap.getOrDefault(
                language,
                DialogCodeAcceptUsage()
            ).copyNumber += 1
        }
    }

    override fun onToolWindowCodeInserted(language: String) {
        updateBehaviorMetrics {
            it.dialogMetric.dialogUsages.acceptUsagesMap.metricsMap.getOrDefault(
                language,
                DialogCodeAcceptUsage()
            ).insertNumber += 1
        }
    }
}
