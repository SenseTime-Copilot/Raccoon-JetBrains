package com.sensetime.sensecore.sensecodeplugin.clients

import com.sensetime.sensecore.sensecodeplugin.settings.ClientConfig
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState
import kotlinx.coroutines.*


object CodeClientManager {
    val client: CodeClient
        get() {
            val clientConfig: ClientConfig = SenseCodeSettingsState.instance.getSelectedClientConfig()
            return _client?.takeIf { it.name == clientConfig.name } ?: clientConfig.constructor().also { _client = it }
        }
    private var _client: CodeClient? = null

    var toolWindowJob: Job? = null
    val clientCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseCodeClient"))
}