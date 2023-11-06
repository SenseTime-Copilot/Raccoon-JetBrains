package com.sensetime.intellij.plugins.sensecode.persistent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin
import kotlin.math.max

@State(
    name = "com.sensetime.intellij.plugins.sensecode.settings.SenseCodeSettingsState",
    storages = [Storage("SenseCodeIntelliJSettings.xml")]
)
data class SenseCodeSettingsState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeSettingsState> {
    // settings
    var candidates: Int = DEFAULT_CANDIDATES
        set(value) {
            field = value.coerceIn(MIN_CANDIDATES, MAX_CANDIDATES)
        }

    var isAutoCompleteMode: Boolean = false
    var autoCompleteDelayMs: Int = DEFAULT_AUTO_COMPLETE_DELAY_MS
        set(value) {
            field = max(MIN_AUTO_COMPLETE_DELAY_MS, value)
        }

    var inlineCompletionPreference: ModelConfig.CompletionPreference = ModelConfig.CompletionPreference.BALANCED

    // only for dev
    var toolwindowMaxNewTokens: Int = -1
    var selectedClientName: String = SenseCoreClient.CLIENT_NAME
    var clientBaseApiMap: Map<String, String> = mapOf(
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

    fun restore() {
        loadState(SenseCodeSettingsState(SenseCodePlugin.version))
    }

    override fun getState(): SenseCodeSettingsState {
        version = SenseCodePlugin.version
        return this
    }

    override fun loadState(state: SenseCodeSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        private const val MIN_CANDIDATES: Int = 1
        private const val MAX_CANDIDATES: Int = 4
        private const val DEFAULT_CANDIDATES: Int = MIN_CANDIDATES
        private const val MIN_AUTO_COMPLETE_DELAY_MS: Int = 1000
        private const val DEFAULT_AUTO_COMPLETE_DELAY_MS: Int = MIN_AUTO_COMPLETE_DELAY_MS

        val instance: SenseCodeSettingsState
            get() = ApplicationManager.getApplication().getService(SenseCodeSettingsState::class.java)
    }
}