package com.sensetime.sensecore.sensecodeplugin.actions.editor

class GenerateTestAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.generateTest(code)
}
