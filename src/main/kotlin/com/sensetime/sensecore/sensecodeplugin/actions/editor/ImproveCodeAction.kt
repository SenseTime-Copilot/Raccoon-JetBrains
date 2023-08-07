package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class ImproveCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.improve(code)
}
