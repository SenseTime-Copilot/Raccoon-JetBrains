package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import kotlin.math.max


@State(
    name = "com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState",
    storages = [Storage("RaccoonJetBrainsSettings.xml")]
)
internal data class RaccoonSettingsState(
    var version: String = ""
) : PersistentStateComponent<RaccoonSettingsState> {
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

    var inlineCompletionColor: String = ""
    var inlineCompletionPreference: CompletionModelConfig.CompletionPreference =
        CompletionModelConfig.CompletionPreference.BEST_EFFORT

    fun restore() {
        loadState(RaccoonSettingsState(RaccoonPlugin.getVersion()))
    }

    override fun getState(): RaccoonSettingsState {
        version = RaccoonPlugin.getVersion()
        return this
    }

    override fun loadState(state: RaccoonSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        const val MIN_CANDIDATES: Int = 1
        const val MAX_CANDIDATES: Int = 3
        private const val DEFAULT_CANDIDATES: Int = MIN_CANDIDATES
        private const val MIN_AUTO_COMPLETE_DELAY_MS: Int = 1000
        private const val DEFAULT_AUTO_COMPLETE_DELAY_MS: Int = MIN_AUTO_COMPLETE_DELAY_MS

        val instance: RaccoonSettingsState
            get() = ApplicationManager.getApplication().getService(RaccoonSettingsState::class.java)
    }
}
