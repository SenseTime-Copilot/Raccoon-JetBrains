package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientOrgException
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.RaccoonClientOrgInfo
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_CLIENT_AUTHORIZATION_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonClientAuthorizationListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotEmpty
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*


internal class UserAuthorizationPanel(
    userName: String?, isCodePro: Boolean,
    currentOrgName: String?, isAvailable: Boolean,
    private val eventListener: EventListener
) : JPanel(BorderLayout()), RaccoonClientAuthorizationListener {
    interface EventListener {
        fun onLoginClicked(parent: Component, onFinallyInsideEdt: () -> Unit)
        fun onLogoutClicked(onFinallyInsideEdt: () -> Unit)
        fun onOrganizationSelected(orgCode: String)
        fun getOrganizations(
            parent: Component,
            onFinallyInsideEdt: (t: Throwable?, List<RaccoonClientOrgInfo>?) -> Unit
        )
    }

    private var isBigMode: Boolean = !RaccoonConfig.config.isTeam()
    private var alreadyLoggedIn: Boolean = false
    private val userIconLabel: JLabel = JLabel()
    private val userNameLabel: JLabel = JLabel("").apply {
        font = JBFont.label().biggerOn(3f).asBold()
    }
    private val loginButton: JButton = RaccoonUIUtils.createActionLink()
    private val currentOrgNameLabel: JLabel = JLabel("").apply {
        isOpaque = true
        font = JBFont.small()
    }
    private val organizationsSelectorButton: LoadingActionButton = LoadingActionButton(
        RaccoonBundle.message("authorization.panel.action.GetOrganizations.text"), "",
        AllIcons.General.GearPlain, ActionPlaces.UNKNOWN, JLabel(AnimatedIcon.Default.INSTANCE)
    ) { e, onFinallyInsideEdt ->
        eventListener.getOrganizations(this) { t, organizations ->
            RaccoonExceptions.resultOf({
                t?.let { throw t }
                organizations?.filter { it.isAvailable() }?.letIfNotEmpty {
                    JBPopupFactory.getInstance().createPopupChooserBuilder(it).setVisibleRowCount(5)
                        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                        .setItemChosenCallback { orgInfo -> eventListener.onOrganizationSelected(orgInfo.code) }
                        .createPopup().showInCenterOf(this)
                }
                    ?: throw LLMClientOrgException(RaccoonBundle.message("authorization.panel.organizations.list.disabled"))
            }, onFinallyInsideEdt).onFailure {
                JBPopupFactory.getInstance().createMessage(it.localizedMessage).showInCenterOf(this)
            }
        }
    }

    private fun updateUserIconLabel() {
        if (alreadyLoggedIn) {
            userIconLabel.icon = if (isBigMode) RaccoonIcons.AUTHENTICATED_USER_BIG else RaccoonIcons.AUTHENTICATED_USER
        } else {
            userIconLabel.icon =
                if (isBigMode) RaccoonIcons.UNAUTHENTICATED_USER_BIG else RaccoonIcons.UNAUTHENTICATED_USER
        }
    }

    override fun onUserNameChanged(userName: String?, isCodePro: Boolean) {
        alreadyLoggedIn = !userName.isNullOrBlank()
        if (!alreadyLoggedIn) {
            loginButton.text = RaccoonBundle.message("authorization.panel.button.login")
            userNameLabel.text = RaccoonBundle.message("authorization.panel.label.unauthenticated")
        } else {
            loginButton.text = RaccoonBundle.message("authorization.panel.button.logout")
            userNameLabel.text = "$userName${if (isCodePro) "(Pro)" else ""}"
        }
        updateUserIconLabel()
    }

    override fun onCurrentOrganizationNameChanged(orgName: String?, isAvailable: Boolean) {
        isBigMode = !(!RaccoonConfig.config.isTeam() || orgName.isNullOrBlank())
        if (isBigMode) {
            currentOrgNameLabel.text = orgName
            currentOrgNameLabel.apply {
                if (isAvailable) {
                    foreground = JBUI.CurrentTheme.NotificationInfo.foregroundColor()
                    background = JBUI.CurrentTheme.NotificationInfo.backgroundColor()
                } else {
                    foreground = JBUI.CurrentTheme.NotificationWarning.foregroundColor()
                    background = JBUI.CurrentTheme.NotificationWarning.backgroundColor()
                }
            }
        }
        loginButton.font = JBFont.label().biggerOn(if (isBigMode) 3f else 1f)
        currentOrgNameLabel.isVisible = isBigMode
        organizationsSelectorButton.isVisible = isBigMode
        updateUserIconLabel()
    }

    init {
        onUserNameChanged(userName, isCodePro)
        onCurrentOrganizationNameChanged(currentOrgName, isAvailable)
        add(panel {
            verticalAlign(VerticalAlign.CENTER)
            row {
                cell(userIconLabel).gap(RightGap.SMALL)
                cell(Box.createVerticalBox().apply {
                    add(userNameLabel.apply {
                        alignmentX = Component.LEFT_ALIGNMENT
                        border = BorderFactory.createEmptyBorder()
                    })
                    add(Box.createHorizontalBox().apply {
                        add(currentOrgNameLabel)
                        add(Box.createHorizontalGlue())
                        add(organizationsSelectorButton.apply {
                            border = BorderFactory.createEmptyBorder()
                        })
                        alignmentX = Component.LEFT_ALIGNMENT
                        border = BorderFactory.createEmptyBorder()
                    })
                }).gap(RightGap.COLUMNS)
                cell(LoadingButton(loginButton, JLabel(AnimatedIcon.Big.INSTANCE)) { _, onFinallyInsideEdt ->
                    if (alreadyLoggedIn) {
                        eventListener.onLogoutClicked(onFinallyInsideEdt)
                    } else {
                        eventListener.onLoginClicked(this@UserAuthorizationPanel, onFinallyInsideEdt)
                    }
                })
            }
        }, BorderLayout.CENTER)
        ApplicationManager.getApplication().messageBus.connect().subscribe(RACCOON_CLIENT_AUTHORIZATION_TOPIC, this)
    }
}
