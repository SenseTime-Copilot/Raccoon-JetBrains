package com.sensetime.sensecore.sensecodeplugin.messagebus

import com.sensetime.sensecore.sensecodeplugin.domain.BasicPrompt
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryItem
import com.intellij.util.messages.Topic
import com.sensetime.sensecore.sensecodeplugin.ui.main.Tab

val CHAT_GPT_ACTION_TOPIC: Topic<ChatGptApiListener> = Topic.create("GptMentorChatGptTopic", ChatGptApiListener::class.java)

interface ChatGptApiListener {
    /**
     * Append explanation to the current explanation. Can be used for streaming backend responses.
     */
    fun onNewPrompt(prompt: BasicPrompt)

    /**
     * Appends new text to an existing prompt.
     */
    fun appendToPrompt(prompt: String)

    /**
     * An item loaded from history will result in a chat which the user can continue.
     */
    fun loadChatFromHistory(historyItem: HistoryItem)
}
