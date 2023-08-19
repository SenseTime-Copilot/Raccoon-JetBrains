package com.sensetime.sensecore.sensecodeplugin.domain

import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest

class PromptFactory(private val state: GptMentorSettingsState) {
    fun generate(code: String) = BasicPrompt.TaskPrompt(code, state.promptCodeGeneration)
    fun generateTest(code: String) = BasicPrompt.TaskPrompt(code, state.promptTestGeneration)
    fun correct(code: String) = BasicPrompt.TaskPrompt(code, state.promptCodeCorrection)
    fun refactor(code: String) = BasicPrompt.TaskPrompt(code, state.promptCodeRefactoring)
    fun chat(messages: List<ChatGptRequest.Message>) = BasicPrompt.Chat(messages, state.systemPromptChat)
    fun promptFromSelection(code: String) = BasicPrompt.PromptFromSelection(code, "")
}
