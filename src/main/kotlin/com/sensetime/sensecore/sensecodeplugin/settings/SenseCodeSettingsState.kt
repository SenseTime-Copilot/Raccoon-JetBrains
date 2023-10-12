package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.clients.SenseCoreClient
import com.sensetime.sensecore.sensecodeplugin.clients.SenseNovaClient
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState",
    storages = [Storage("SenseCodeIntelliJSettings.xml")]
)
data class SenseCodeSettingsState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeSettingsState> {
    var candidates: Int = 1
    var isAutoCompleteMode: Boolean = false
    var autoCompleteDelayMs: Int = 1000
    var inlineCompletionPreference: ModelConfig.CompletionPreference = ModelConfig.CompletionPreference.BALANCED

    var selectedClientName: String = SenseCoreClient.CLIENT_NAME
    var clientApiEndpointMap: Map<String, String> = mapOf(
        SenseCoreClient.CLIENT_NAME to SenseCoreClient.API_ENDPOINT,
        SenseNovaClient.CLIENT_NAME to SenseNovaClient.API_ENDPOINT
    )
    private val clients: Map<String, ClientConfig> =
        mapOf(
            SenseCoreClient.CLIENT_NAME to SenseCoreClient.getDefaultClientConfig(),
            SenseNovaClient.CLIENT_NAME to SenseNovaClient.getDefaultClientConfig()
        )
    val selectedClientConfig: ClientConfig
        get() = selectedClientName.let {
            clients.getValue(it).apply { apiEndpoint = clientApiEndpointMap.getValue(it) }
        }

    // dev
    var toolwindowMaxNewTokens: Int = -1

    fun restore() {
        loadState(SenseCodeSettingsState(SenseCodePlugin.version))
    }

    override fun getState(): SenseCodeSettingsState {
        if (version != SenseCodePlugin.version) {
            version = SenseCodePlugin.version
        }
        return this
    }

    override fun loadState(state: SenseCodeSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SenseCodeSettingsState
            get() = ApplicationManager.getApplication().getService(SenseCodeSettingsState::class.java)
    }
}
