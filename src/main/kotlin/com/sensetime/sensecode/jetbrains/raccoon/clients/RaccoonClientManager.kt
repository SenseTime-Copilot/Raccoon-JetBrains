package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonCredentialsListener
import kotlinx.coroutines.*

object RaccoonClientManager {
    class CredentialsListener : RaccoonCredentialsListener {
        override fun onClientAuthChanged(name: String, key: String) {
            currentCodeClient.takeIf { name == it.name }?.let { client ->
                ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC)
                    .run {
                        onUserNameChanged(client.userName)
                        onAlreadyLoggedInChanged(client.alreadyLoggedIn)
                    }
            }
        }
    }

    private var _client: CodeClient? = null
    private val clientConstructorMap = mapOf(SenseChatOnlyLoginClient.CLIENT_NAME to ::SenseChatOnlyLoginClient)

    val clientAndConfigPair: Pair<CodeClient, ClientConfig>
        get() = RaccoonSettingsState.selectedClientConfig.let { clientConfig ->
            Pair(_client?.takeIf { it.name == clientConfig.name }
                ?: clientConstructorMap.getValue(clientConfig.name)().also { _client = it }, clientConfig)
        }

    val userName: String?
        get() = currentCodeClient.userName

    val currentCodeClient: CodeClient
        get() = clientAndConfigPair.first

    val clientCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("RaccoonClient"))

    fun login(): Job = clientCoroutineScope.launch {
        currentCodeClient.run {
            if (alreadyLoggedIn) {
                logout()
            } else {
                login()
            }
        }
    }

    private var updateLoginResultJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    fun updateLoginResult(name: String, block: suspend (CodeClient) -> Unit) {
        updateLoginResultJob = currentCodeClient.takeIf { it.name == name }?.let {
            clientCoroutineScope.launch {
                block(it)
            }
        }
    }
}