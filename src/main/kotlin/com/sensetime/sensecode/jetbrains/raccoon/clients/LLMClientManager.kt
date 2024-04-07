package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMChatRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.requests.LLMCompletionRequest
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChatChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMChoice
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.LLMCompletionChoice
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import kotlinx.coroutines.*
import java.awt.Component


@Service(Service.Level.PROJECT)
internal class LLMClientManager(private var project: Project) : Disposable {
    private val clientCoroutineScope: CoroutineScope =
        MainScope() + CoroutineName("${currentLLMClient.name}: ${project.name}")


    interface ClientJobStateListener {
        fun onFailureWithoutCancellationInsideEdt(t: Throwable)
        fun onFinallyInsideEdt() {}
    }

    interface LLMJobListener<T : LLMChoice, R> : LLMClient.LLMResponseListener<T, R>, ClientJobStateListener

    private fun launchClientJob(blockInsideEdt: suspend CoroutineScope.(LLMClient) -> Unit): Job =
        clientCoroutineScope.launch(Dispatchers.Main.immediate) { blockInsideEdt(currentLLMClient) }

    private fun launchClientJobWithCatching(
        clientJobStateListener: ClientJobStateListener,
        blockInsideEdtAndCatching: suspend CoroutineScope.(LLMClient) -> Unit
    ): Job = launchClientJob { llmClient ->
        RaccoonExceptions.resultOf(
            { blockInsideEdtAndCatching(llmClient) }, clientJobStateListener::onFinallyInsideEdt
        ).onFailure(clientJobStateListener::onFailureWithoutCancellationInsideEdt)
    }

    fun <R> launchLLMCompletionJob(
        isEnableNotify: Boolean,
        uiComponentForEdt: Component,
        llmCompletionRequest: LLMCompletionRequest,
        llmCompletionJobListener: LLMJobListener<LLMCompletionChoice, R>
    ): Job = launchClientJobWithCatching(llmCompletionJobListener) { llmClient ->
        llmClient.runLLMCompletionJob(
            isEnableNotify, project, uiComponentForEdt,
            llmCompletionRequest, llmCompletionJobListener
        )
    }

    fun <R> launchLLMChatJob(
        isEnableNotify: Boolean,
        uiComponentForEdt: Component,
        llmChatRequest: LLMChatRequest,
        llmChatJobListener: LLMJobListener<LLMChatChoice, R>
    ): Job = launchClientJobWithCatching(llmChatJobListener) { llmClient ->
        llmClient.runLLMChatJob(isEnableNotify, project, uiComponentForEdt, llmChatRequest, llmChatJobListener)
    }

    override fun dispose() {
        clientCoroutineScope.cancel()
    }

    companion object {
        private val currentLLMClient: LLMClient = RaccoonClient()
        fun getInstance(project: Project): LLMClientManager = project.service()
    }
}
