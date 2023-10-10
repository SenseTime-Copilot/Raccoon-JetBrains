package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_CLIENTS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messages.SenseCodeCredentialsListener
import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState
import kotlinx.coroutines.*

object CodeClientManager {
    class CredentialsListener : SenseCodeCredentialsListener {
        override fun onClientAuthChanged(name: String, key: String) {
            getClientAndConfigPair().first.takeIf { name == it.name }?.let { client ->
                ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CLIENTS_TOPIC).run {
                    onUserNameChanged(client.userName)
                    onAlreadyLoggedInChanged(client.alreadyLoggedIn)
                }
            }
        }
    }

    private var _client: CodeClient? = null
    fun getClientAndConfigPair(): Pair<CodeClient, ClientConfig> {
        val clientConfig: ClientConfig = SenseCodeSettingsState.instance.selectedClientConfig
        return Pair(_client?.takeIf { it.name == clientConfig.name } ?: clientConfig.constructor()
            .also { _client = it }, clientConfig)
    }

    fun getUserName(): String? = getClientAndConfigPair().first.userName

    fun login(): Job = otherCoroutineScope.launch {
        val (client, config) = getClientAndConfigPair()
        (if (client.alreadyLoggedIn) client::logout else client::login)(config.apiEndpoint)
    }

    private val otherCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseCodeOther"))

    val clientCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseCodeClient"))
}