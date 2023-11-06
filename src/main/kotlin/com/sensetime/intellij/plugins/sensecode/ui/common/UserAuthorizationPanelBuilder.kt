package com.sensetime.intellij.plugins.sensecode.ui.common

import ai.grazie.utils.applyIfNotNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.dsl.builder.*
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.JBFont
import com.sensetime.intellij.plugins.sensecode.clients.CodeClient
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeIcons
import com.sensetime.intellij.plugins.sensecode.topics.SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.intellij.plugins.sensecode.topics.SenseCodeClientAuthorizationListener
import com.sensetime.intellij.plugins.sensecode.utils.takeIfNotBlank
import kotlinx.coroutines.Job
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
        akskPasswordRow(akskSettings.skItem).applyIfNotNull(akskSettings.groupComment?.takeIfNotBlank()) {
            rowComment(it)
        }
    }

class UserAuthorizationPanelBuilder : Disposable {
    private var loginJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var clientAuthorizationMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    private var akskGroup: CollapsibleRow? = null
    private val userIconLabel: JLabel = JLabel()
    private val userNameLabel: JLabel = JLabel("").apply { font = JBFont.label().biggerOn(3f).asBold() }
    private val loginButton: LoadingButton = LoadingButton(
        this,
        SenseCodeUIUtils.createActionLinkBiggerOn1(),
        JLabel(AnimatedIcon.Big.INSTANCE)
    ) { _, onCompletion -> loginJob = SenseCodeClientManager.login().apply { invokeOnCompletion { onCompletion() } } }

    private fun setUserName(userName: String?) {
        if (userName.isNullOrBlank()) {
            userIconLabel.icon = SenseCodeIcons.UNAUTHENTICATED_USER
            userNameLabel.text = "unauthenticated"
        } else {
            userIconLabel.icon = SenseCodeIcons.AUTHENTICATED_USER
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

    fun build(parent: Disposable, akskSettings: CodeClient.AkSkSettings? = null): DialogPanel {
        val client = SenseCodeClientManager.currentCodeClient
        setUserName(client.userName)
        setAlreadyLoggedIn(client.alreadyLoggedIn)
        setIsSupportLogin(client.isSupportLogin)

        val userAuthorizationPanel: DialogPanel = panel {
            align(AlignY.CENTER)
            row {
                cell(userIconLabel).gap(RightGap.SMALL)
                cell(userNameLabel).gap(RightGap.COLUMNS)
                cell(loginButton.button)
                cell(loginButton.loading)
            }
            akskGroup = akskSettings?.let { akskCollapsibleRow(it) }
        }

        clientAuthorizationMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC, object : SenseCodeClientAuthorizationListener {
                override fun onUserNameChanged(userName: String?) {
                    userAuthorizationPanel.invokeOnUIThreadLater { setUserName(userName) }
                }

                override fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {
                    userAuthorizationPanel.invokeOnUIThreadLater { setAlreadyLoggedIn(alreadyLoggedIn) }
                }
            })
        }

        Disposer.register(parent, this)
        return userAuthorizationPanel
    }

    override fun dispose() {
        loginJob = null
        clientAuthorizationMessageBusConnection = null
    }
}