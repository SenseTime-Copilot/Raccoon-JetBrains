package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.clients.SenseNovaClient
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState",
    storages = [Storage("SenseCodeIntelliJSettings.xml")]
)
data class SenseCodeSettingsState(
    var version: String = SenseCodePlugin.version
) : PersistentStateComponent<SenseCodeSettingsState> {
    val selectedClientName: String = SenseNovaClient.CLIENT_NAME
    fun getSelectedClientConfig(): ClientConfig = clients.getValue(selectedClientName)
    val clients: Map<String, ClientConfig> =
        mapOf(SenseNovaClient.CLIENT_NAME to SenseNovaClient.getDefaultClientConfig())

    var candidates: Int = 1
    var isAutoCompleteMode: Boolean = false
    var autoCompleteDelayMs: Int = 1000
    var completionPreference: ModelConfig.CompletionPreference = ModelConfig.CompletionPreference.BALANCED

    fun restore() {
        loadState(SenseCodeSettingsState())
    }

    override fun getState(): SenseCodeSettingsState = this
    override fun loadState(state: SenseCodeSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SenseCodeSettingsState
            get() = ApplicationManager.getApplication().getService(SenseCodeSettingsState::class.java)
    }
}
