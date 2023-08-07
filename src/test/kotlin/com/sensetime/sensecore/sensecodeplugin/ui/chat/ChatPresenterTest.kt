package com.sensetime.sensecore.sensecodeplugin.ui.chat

import com.sensetime.sensecore.sensecodeplugin.domain.BasicPrompt
import com.sensetime.sensecore.sensecodeplugin.domain.Model
import com.sensetime.sensecore.sensecodeplugin.openapi.OpenApi
import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryRepository
import com.sensetime.sensecore.sensecodeplugin.ui.main.MainPresenter
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore
@ExperimentalCoroutinesApi
class ChatPresenterTest {

    private lateinit var presenter: ChatPresenter
    private val chatView: ChatView = mockk(relaxed = true)
    private val openApi: OpenApi = mockk(relaxed = true)
    private val mainPresenter: MainPresenter = mockk(relaxed = true)

    private val prompt = BasicPrompt.ExplainCode("action", "")
    private val message = ChatGptRequest.Message.newUserMessage("action")

    private val historyRepository: HistoryRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        presenter = ChatPresenter(chatView, mainPresenter, openApi, historyRepository)
    }

    @Test
    fun `onSubmitClicked should set prompt and execute streaming with new user message`() {
        every { chatView.getPrompt() } returns "action"
        presenter.onSubmitClicked()

        coVerify {
            chatView.setPrompt("action")
            chatView.appendToExplanation("action")
            openApi.executeBasicActionStreaming(
                BasicPrompt.Chat(listOf(message), "").createRequest(Model.GPT_3_5_TURBO, 0.7f, 1024)
            )
        }
    }

    @Test
    fun `onNewPrompt should set prompt and execute streaming`() {
        presenter.onNewPrompt(prompt)

        coVerify {
            chatView.setPrompt(prompt.action)
            chatView.clearExplanation()
            openApi.executeBasicActionStreaming(prompt.createRequest(Model.GPT_3_5_TURBO, 0.7f, 1024))
        }
    }

    @Test
    fun `onNewChatClicked should clear all views and reset chat`() {
        presenter.onNewChatClicked()

        coVerify {
            chatView.clearAll()
            chatView.setFocusOnPrompt()
        }
    }

    @Test
    fun `submit button is clicked with empty prompt`() {
        every { chatView.getPrompt() } returns ""

        presenter.onSubmitClicked()

        coVerify {
            openApi wasNot Called
        }
    }
}

