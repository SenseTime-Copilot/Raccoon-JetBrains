package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import ai.grazie.utils.applyIfNotNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.dsl.builder.*
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.JBFont
import com.sensetime.sensecode.jetbrains.raccoon.clients.CodeClient
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonClientAuthorizationListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.takeIfNotBlank
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

    private var alreadyLoggedIn: Boolean = false
        set(value) {
            loginButton.button.text =
                RaccoonBundle.message(if (value) "authorization.panel.button.logout" else "authorization.panel.button.login")
            akskGroup?.expanded = !value
            field = value
        }

    private var akskGroup: CollapsibleRow? = null
    private val userIconLabel: JLabel = JLabel()
    private val userNameLabel: JLabel = JLabel("").apply { font = JBFont.label().biggerOn(3f).asBold() }
    private val loginButton: LoadingButton = RaccoonUIUtils.createActionLinkBiggerOn1().let { loginActionLink ->
        LoadingButton(this, loginActionLink, JLabel(AnimatedIcon.Big.INSTANCE)) { _, onCompletion ->
            loginJob = if (alreadyLoggedIn) {
                RaccoonClientManager.launchClientJob { kotlin.runCatching { it.logout() } }
                    .apply { invokeOnCompletion { onCompletion() } }
            } else {
                LoginDialog(null, loginActionLink).showAndGet()
                onCompletion()
                null
            }
        }
    }

    private fun setUserName(userName: String?) {
        if (userName.isNullOrBlank()) {
            userIconLabel.icon = RaccoonIcons.UNAUTHENTICATED_USER
            userNameLabel.text = "unauthenticated"
        } else {
            userIconLabel.icon = RaccoonIcons.AUTHENTICATED_USER
            userNameLabel.text = userName
        }
    }

    private fun setIsSupportLogin(isSupportLogin: Boolean) {
        loginButton.isEnabled = isSupportLogin
        if (!isSupportLogin) {
            loginButton.button.toolTipText =
                RaccoonBundle.message("authorization.panel.button.tooltip.LoginNotSupported")
        }
    }

    fun build(parent: Disposable, akskSettings: CodeClient.AkSkSettings? = null): DialogPanel {
        val client = RaccoonClientManager.currentCodeClient
        setUserName(client.userName)
        alreadyLoggedIn = client.alreadyLoggedIn
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
            it.subscribe(SENSE_CODE_CLIENT_AUTHORIZATION_TOPIC, object : RaccoonClientAuthorizationListener {
                override fun onUserNameChanged(userName: String?) {
                    userAuthorizationPanel.invokeOnUIThreadLater { setUserName(userName) }
                }

                override fun onAlreadyLoggedInChanged(alreadyLoggedIn: Boolean) {
                    userAuthorizationPanel.invokeOnUIThreadLater {
                        this@UserAuthorizationPanelBuilder.alreadyLoggedIn = alreadyLoggedIn
                    }
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