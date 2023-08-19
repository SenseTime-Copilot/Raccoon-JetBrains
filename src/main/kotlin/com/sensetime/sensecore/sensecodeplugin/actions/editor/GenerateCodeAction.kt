package com.sensetime.sensecore.sensecodeplugin.actions.editor

class GenerateCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.generate(code)
}
