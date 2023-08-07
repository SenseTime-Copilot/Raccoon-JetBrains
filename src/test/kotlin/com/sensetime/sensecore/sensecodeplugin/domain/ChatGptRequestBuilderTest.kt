package com.sensetime.sensecore.sensecodeplugin.domain

import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequestBuilder
import org.junit.Assert.*
import org.junit.Test

class ChatGptRequestBuilderTest {
    @Test
    fun testBuilder() {
        // Given
        val expectedRequest = ChatGptRequest(
            model = Model.GPT_4,
            temperature = 0.9f,
            maxTokens = 2048,
            messages = listOf(
                ChatGptRequest.Message(ChatGptRequest.Message.Role.USER, "Hello!"),
                ChatGptRequest.Message(ChatGptRequest.Message.Role.SYSTEM, "Hi there!")
            )
        )

        // When
        val actualRequest = ChatGptRequestBuilder().apply {
            model = Model.GPT_4
            temperature = 0.9f
            maxTokens = 2048
            message {
                role = ChatGptRequest.Message.Role.USER
                content = "Hello!"
            }
            message {
                role = ChatGptRequest.Message.Role.SYSTEM
                content = "Hi there!"
            }
        }.build()

        // Then
        assertEquals(expectedRequest, actualRequest)
    }
}
