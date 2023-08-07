package com.sensetime.sensecore.sensecodeplugin.actions.editor

import com.sensetime.sensecore.sensecodeplugin.actions.editor.BaseSimpleChatGptAction

class ExplainCodeAction : BaseSimpleChatGptAction() {
    override fun createPrompt(code: String) = promptFactory.explain(code)
}
