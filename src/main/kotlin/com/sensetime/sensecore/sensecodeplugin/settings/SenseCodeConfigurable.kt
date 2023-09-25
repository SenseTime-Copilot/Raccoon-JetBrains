package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_CLIENTS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messages.SenseCodeClientsListener
import kotlinx.coroutines.Job
import javax.swing.JComponent

class SenseCodeConfigurable : Configurable, SenseCodeClientsListener {
    private var loginJob: Job? = null
    private var clientMessageBusConnection: SimpleMessageBusConnection? = null

    private var settingsPanel: DialogPanel? = null
    private var settingsComponent: SenseCodeSettingsComponent? = null

    override fun onUserNameChanged(userName: String?) {
        ApplicationManager.getApplication()
            .invokeLater({ settingsComponent?.setUserName(userName) }, ModalityState.stateForComponent(settingsPanel!!))
    }

    override fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {
        ApplicationManager.getApplication().invokeLater(
            { settingsComponent?.setAlreadyLoggedIn(alreadyLoggedIn) },
            ModalityState.stateForComponent(settingsPanel!!)
        )
    }

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
        clientMessageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            .also { it.subscribe(SENSE_CODE_CLIENTS_TOPIC, this) }
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

    override fun disposeUIResources() {
        super.disposeUIResources()
        cancelLoginJob()
        disconnectClientMessageBusConnection()

        settingsPanel = null
        settingsComponent = null
    }

    private fun disconnectClientMessageBusConnection() {
        clientMessageBusConnection?.disconnect()
        clientMessageBusConnection = null
    }

    private fun cancelLoginJob() {
        loginJob?.cancel()
        loginJob = null
    }
}