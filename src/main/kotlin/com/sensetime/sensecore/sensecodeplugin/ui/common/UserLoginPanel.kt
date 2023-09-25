package com.sensetime.sensecore.sensecodeplugin.ui.common

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.JLabel

open class UserLoginPanel {
    fun setUserName(userName: String?) {
        userNameLabel.text = userName ?: "unauthorized"
    }

    open fun setAlreadyLoggedIn(alreadyLoggedIn: Boolean) {
        loginButton.button.text =
            SenseCodeBundle.message(if (alreadyLoggedIn) "user.button.logout" else "user.button.login")
    }

    fun setIsSupportLogin(isSupportLogin: Boolean) {
        loginButton.setIsEnabled(isSupportLogin)
        if (!isSupportLogin) {
            loginButton.button.toolTipText = SenseCodeBundle.message("user.tooltip.LoginNotSupported")
        }
    }

    fun setOnLoginClick(onLoginClick: ((ActionEvent, () -> Unit) -> Unit)? = null) {
        loginButton.setOnClick(onLoginClick)
    }

    protected fun setupUserLoginPanel(
        userName: String?,
        alreadyLoggedIn: Boolean,
        isSupportLogin: Boolean,
        onLoginClick: ((ActionEvent, () -> Unit) -> Unit)? = null
    ) {
        setUserName(userName)
        setAlreadyLoggedIn(alreadyLoggedIn)
        setIsSupportLogin(isSupportLogin)
        setOnLoginClick(onLoginClick)
    }

    fun getUserLoginPanel(
        userName: String?,
        alreadyLoggedIn: Boolean,
        isSupportLogin: Boolean,
        onLoginClick: ((ActionEvent, () -> Unit) -> Unit)? = null
    ): DialogPanel {
        setupUserLoginPanel(userName, alreadyLoggedIn, isSupportLogin, onLoginClick)
        return userLoginPanel
    }

    private val userNameLabel: JLabel = JLabel("").apply { font = remakeFont(font, USER_FONT_SCALE, Font.BOLD) }
    private val loginButton: LoadingButton = LoadingButton(
        ActionLink().apply {
            autoHideOnDisable = false
            font = remakeFont(font, USER_FONT_SCALE)
        },
        JLabel(AnimatedIcon.Big.INSTANCE)
    )
    protected val userLoginPanel: DialogPanel = panel {
        align(AlignY.CENTER)
        row {
            icon(SenseCodeIcons.DEFAULT_USER_AVATAR).gap(RightGap.SMALL)
            cell(userNameLabel).gap(RightGap.COLUMNS)
            cell(loginButton.button)
            cell(loginButton.loading)
        }
    }

    companion object {
        private const val USER_FONT_SCALE = 1.3f

        @JvmStatic
        private fun remakeFont(font: Font, scale: Float, style: Int = 0): Font =
            Font(font.name, (font.style or style), (font.size * scale).toInt())
    }
}