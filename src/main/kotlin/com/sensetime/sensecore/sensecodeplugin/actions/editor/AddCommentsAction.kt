package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class AddCommentsAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.addComments(code)
}
