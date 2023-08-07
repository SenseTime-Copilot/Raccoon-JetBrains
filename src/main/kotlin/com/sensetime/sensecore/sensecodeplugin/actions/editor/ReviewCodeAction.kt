package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class ReviewCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.review(code)
}
