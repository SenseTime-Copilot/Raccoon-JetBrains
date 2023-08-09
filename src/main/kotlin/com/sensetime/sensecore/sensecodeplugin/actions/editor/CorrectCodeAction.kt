package com.sensetime.sensecore.sensecodeplugin.actions.editor

class CorrectCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.correct(code)
}
