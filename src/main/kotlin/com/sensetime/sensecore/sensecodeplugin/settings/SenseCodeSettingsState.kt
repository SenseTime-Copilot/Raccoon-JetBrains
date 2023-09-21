package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.clients.SenseCoreClient

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeSettingsState",
    storages = [Storage("SenseCodeSettings.xml")]
)
data class SenseCodeSettingsState(
    var completionPreference: ModelConfig.CompletionPreference = ModelConfig.CompletionPreference.BALANCED
) : PersistentStateComponent<SenseCodeSettingsState> {

    var selectedClientIndex: Int = 0
        set(value) {
            if (value < 0 || value >= clients.size) {
                throw IndexOutOfBoundsException("Set invalid selectedClientIndex value($value), clients size(${clients.size})")
            }
            field = value
        }
    val clients: List<ClientConfig> = listOf(SenseCoreClient.getDefaultClientConfig())
    fun getSelectedClientConfig(): ClientConfig = clients[selectedClientIndex]

    var isStream: Boolean = true
    var candidates: Int = 1
    var isAutoCompleteMode: Boolean = false
    var autoCompleteDelayMs: Int = 1000

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
