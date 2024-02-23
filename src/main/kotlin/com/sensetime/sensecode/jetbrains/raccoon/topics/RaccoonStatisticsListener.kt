package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.util.messages.Topic

@Topic.AppLevel
val RACCOON_STATISTICS_TOPIC = Topic.create("RaccoonStatisticsTopic", RaccoonStatisticsListener::class.java)

interface RaccoonStatisticsListener {
    fun onGenerateGitCommitMessageFinished()

    fun onInlineCompletionFinished(language: String)
    fun onInlineCompletionAccepted(language: String)

    fun onToolWindowNewSession()
    fun onToolWindowQuestionSubmitted()
    fun onToolWindowAnswerFinished()
    fun onToolWindowRegenerateFinished()

    fun onToolWindowCodeGenerated(language: List<String>)
    fun onToolWindowCodeCopied(language: String)
    fun onToolWindowCodeInserted(language: String)
}