package com.github.jcraane.gptmentorplugin.actions

import com.github.jcraane.gptmentorplugin.openapi.BasicPrompt
import com.github.jcraane.gptmentorplugin.openapi.StreamingResponse
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.collect

class ExplainCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = BasicPrompt.ExplainCode(code)
}
