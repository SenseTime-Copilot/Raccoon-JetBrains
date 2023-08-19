package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class PromptFromSelectionAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.promptFromSelection(code)
}
