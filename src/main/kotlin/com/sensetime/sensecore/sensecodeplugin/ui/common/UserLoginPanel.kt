package com.sensetime.sensecore.sensecodeplugin.ui.common

import ai.grazie.utils.applyIfNotNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.JBFont
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_CLIENTS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messages.SenseCodeClientsListener
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import kotlinx.coroutines.Job
import java.awt.Font
import javax.swing.JLabel

internal fun Panel.akskPasswordRow(item: CodeClient.AkSkSettingsItem): Row =
    row(item.label) {
        passwordField().bindText(item.getter, item.setter).component.toolTipText = item.toolTipText
    }

internal fun Panel.akskCollapsibleRow(akskSettings: CodeClient.AkSkSettings): CollapsibleRow =
    collapsibleGroup(akskSettings.groupTitle) {
        akskSettings.akItem?.let { ak ->
            akskPasswordRow(ak)
        }
        akskPasswordRow(akskSettings.skItem).applyIfNotNull(akskSettings.groupComment?.takeIf { it.isNotBlank() }) {
            rowComment(it)
        }
    }

class UserLoginPanel(parent: Disposable, akskSettings: CodeClient.AkSkSettings? = null) : Disposable {
    private var akskGroup: CollapsibleRow? = null
    private val userIconLabel: JLabel = JLabel()
    private val userNameLabel: JLabel = JLabel("").apply { font = JBFont.label().biggerOn(3f).asBold() }
    private val loginButton: LoadingButton = LoadingButton(
        this,
        ActionLink().apply {
            isFocusPainted = false
            autoHideOnDisable = false
            font = JBFont.label().biggerOn(1f)
        },
        JLabel(AnimatedIcon.Big.INSTANCE)
    ) { _, onCompletion -> loginJob = CodeClientManager.login().apply { invokeOnCompletion { onCompletion() } } }

    private var loginJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var clientMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    val userLoginPanel: DialogPanel = panel {
        align(AlignY.CENTER)
        row {
            cell(userIconLabel).gap(RightGap.SMALL)
            cell(userNameLabel).gap(RightGap.COLUMNS)
            cell(loginButton.button)
            cell(loginButton.loading)
        }
        akskGroup = akskSettings?.let {
            akskCollapsibleRow(it)
        }
    }

    init {
        val (client, _) = CodeClientManager.getClientAndConfigPair()
        setUserName(client.userName)
        setAlreadyLoggedIn(client.alreadyLoggedIn)
        setIsSupportLogin(client.isSupportLogin)

        clientMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(SENSE_CODE_CLIENTS_TOPIC, object : SenseCodeClientsListener {
                override fun onUserNameChanged(userName: String?) {
                    ApplicationManager.getApplication()
                        .invokeLater({ setUserName(userName) }, ModalityState.stateForComponent(userLoginPanel))
                }

                override fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {
                    ApplicationManager.getApplication().invokeLater(
                        { setAlreadyLoggedIn(alreadyLoggedIn) },
                        ModalityState.stateForComponent(userLoginPanel)
                    )
                }
            })
        }

        Disposer.register(parent, this)
    }

    override fun dispose() {
        loginJob = null
        clientMessageBusConnection = null
    }

    private fun setUserName(userName: String?) {
        if (userName.isNullOrBlank()) {
            userIconLabel.icon = SenseCodeIcons.NOT_LOGGED_USER
            userNameLabel.text = "unauthorized"
        } else {
            userIconLabel.icon = SenseCodeIcons.LOGGED_USER
            userNameLabel.text = userName
        }
    }

    private fun setAlreadyLoggedIn(alreadyLoggedIn: Boolean) {
        loginButton.button.text =
            SenseCodeBundle.message(if (alreadyLoggedIn) "user.button.logout" else "user.button.login")
        akskGroup?.expanded = !alreadyLoggedIn
    }

    private fun setIsSupportLogin(isSupportLogin: Boolean) {
        loginButton.isEnabled = isSupportLogin
        if (!isSupportLogin) {
            loginButton.button.toolTipText = SenseCodeBundle.message("user.tooltip.LoginNotSupported")
        }
    }
}