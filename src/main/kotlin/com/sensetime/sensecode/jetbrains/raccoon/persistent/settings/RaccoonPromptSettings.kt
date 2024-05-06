package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.PromptVariables
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.replaceVariables
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonPersistentStateComponent
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlank


@Service
@State(
    name = "com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonPromptSettings",
    storages = [Storage("RaccoonJetBrainsSettings.xml")]
)
internal class RaccoonPromptSettings : RaccoonPersistentStateComponent<RaccoonPromptSettings.State>(State()) {
    class State : RaccoonPersistentStateComponent.State() {
        var commitPromptTemplate by string()
    }

    var commitPromptTemplate: String
        get() = state.commitPromptTemplate.ifNullOrBlank("Here are changes of current codebase:\n\n```diff\n${PromptVariables.diffExpression}\n```\n\nWrite a commit message summarizing these changes, not have to cover erevything, key-points only. Response the content only, limited the message to 50 characters, in plain text format, and without quotation marks.")
        set(value) {
            state.commitPromptTemplate = value
        }

    fun getCommitPrompt(diff: String): String =
        commitPromptTemplate.replaceVariables(mapOf(PromptVariables.DIFF to diff))

    companion object {
        fun getInstance(): RaccoonPromptSettings = service()
    }
}
