package com.sensetime.intellij.plugins.sensecode.persistent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.intellij.plugins.sensecode.clients.SenseChatOnlyLoginClient
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin
import kotlin.math.max

@State(
    name = "com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState",
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
    private val selectedClientName: String = SenseChatOnlyLoginClient.CLIENT_NAME
    var clientBaseUrlMap: Map<String, String> =
        mapOf(SenseChatOnlyLoginClient.CLIENT_NAME to SenseChatOnlyLoginClient.BASE_API)
    private val clients: Map<String, ClientConfig> = listOf(SenseChatOnlyLoginClient.defaultClientConfig).toMap()

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
        const val MIN_CANDIDATES: Int = 1
        const val MAX_CANDIDATES: Int = 3
        private const val DEFAULT_CANDIDATES: Int = MIN_CANDIDATES
        private const val MIN_AUTO_COMPLETE_DELAY_MS: Int = 1000
        private const val DEFAULT_AUTO_COMPLETE_DELAY_MS: Int = MIN_AUTO_COMPLETE_DELAY_MS

        val instance: SenseCodeSettingsState
            get() = ApplicationManager.getApplication().getService(SenseCodeSettingsState::class.java)

        val selectedClientConfig: ClientConfig
            get() = instance.let { it.clients.getValue(it.selectedClientName) }
    }
}