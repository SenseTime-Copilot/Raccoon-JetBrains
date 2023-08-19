package com.sensetime.sensecore.sensecodeplugin.actions.editor

class RefactorCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.refactor(code)
}
