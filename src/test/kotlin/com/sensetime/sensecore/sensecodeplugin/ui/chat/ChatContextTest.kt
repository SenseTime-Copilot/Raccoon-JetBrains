package com.sensetime.sensecore.sensecodeplugin.ui.chat

import com.sensetime.sensecore.sensecodeplugin.domain.BasicPrompt
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import org.junit.Assert.*
import org.junit.Test

class ChatContextTest {
    @Test
    fun testFromPrompt() {
        // given
        val id = "123"
        val prompt = BasicPrompt.TaskPrompt("code", "system message")

        // when
        val chatContext = ChatContext.fromPrompt(id, prompt)

        // then
        assertEquals(id, chatContext.chatId)
        assertTrue(chatContext.chat is BasicPrompt.Chat)
        val chat = chatContext.chat as BasicPrompt.Chat
        assertEquals(1, chat.messages.size)
        assertEquals(ChatGptRequest.Message.newUserMessage(prompt.action), chat.messages[0])
        assertEquals(prompt.systemPrompt, chat.systemMessage)
    }
}
