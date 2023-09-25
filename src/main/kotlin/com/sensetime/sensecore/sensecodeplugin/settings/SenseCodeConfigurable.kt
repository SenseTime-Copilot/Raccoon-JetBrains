package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import kotlinx.coroutines.Job
import javax.swing.JComponent

class SenseCodeConfigurable : Configurable {
    private var loginJob: Job? = null
    private var settingsPanel: DialogPanel? = null
    private var settingsComponent: SenseCodeSettingsComponent? = null

    override fun createComponent(): JComponent? {
        val (client, _) = CodeClientManager.getClientAndConfigLet()
        settingsComponent = SenseCodeSettingsComponent(client.getAkSkSettings())
        settingsPanel = settingsComponent?.getSettingsPanel(
            client.userName,
            client.alreadyLoggedIn,
            client.isSupportLogin
        ) { _, onCompletion ->
            loginJob = CodeClientManager.login().apply {
                invokeOnCompletion { onCompletion() }
            }
        }
        return settingsPanel
    }

    override fun getDisplayName(): String = "SenseCode"
    override fun isModified(): Boolean = (true == settingsPanel?.isModified())

    override fun apply() {
        settingsPanel?.apply()
    }

    override fun reset() {
        super.reset()
        settingsPanel?.reset()
    }

    override fun cancel() {
        super.cancel()
        cancelLoginJob()
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        cancelLoginJob()
        settingsPanel = null
        settingsComponent = null
    }

    private fun cancelLoginJob() {
        loginJob?.cancel()
        loginJob = null
    }
}