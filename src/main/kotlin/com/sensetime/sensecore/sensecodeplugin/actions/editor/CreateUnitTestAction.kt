package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class CreateUnitTestAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.createUnitTest(code)
}
