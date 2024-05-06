package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
internal val RACCOON_STATISTICS_TOPIC = Topic.create("RaccoonStatisticsTopic", RaccoonStatisticsListener::class.java)

internal interface RaccoonStatisticsListener {
    fun onGenerateGitCommitMessageFinished()

    fun onInlineCompletionFinished(language: String, candidates: Int)
    fun onInlineCompletionAccepted(language: String)

    fun onToolWindowNewSession()
    fun onToolWindowQuestionSubmitted()
    fun onToolWindowAnswerFinished()
    fun onToolWindowRegenerateClicked()

    fun onToolWindowCodeGenerated(languages: List<String>)
    fun onToolWindowCodeCopied(language: String)
    fun onToolWindowCodeInserted(language: String)
}