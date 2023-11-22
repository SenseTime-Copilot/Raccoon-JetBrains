package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.*
import java.awt.Component
import java.util.*
import javax.swing.JComponent
import kotlin.coroutines.cancellation.CancellationException

class LoginDialog(
    project: Project?, parent: Component?
) : DialogWrapper(
    project, parent, false, IdeModalityType.PROJECT
) {
    private var phoneField: JBTextField? = null
    private var passwordField: JBPasswordField? = null
    private var loginError: ValidationInfo? = null
    private var loginJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    init {
        title = RaccoonBundle.message("authorization.panel.button.login")
        stopLoading()
        init()

        Disposer.register(disposable) {
            loginJob = null
        }
    }

    private fun startLoading() {
        setOKButtonText(RaccoonBundle.message("login.dialog.button.loggingIn"))
        isOKActionEnabled = false
        loginError = null
    }

    private fun stopLoading(error: String? = null) {
        setOKButtonText(RaccoonBundle.message("authorization.panel.button.login"))
        isOKActionEnabled = true
        loginError = error?.letIfNotBlank { ValidationInfo(it) }
    }

    override fun getPreferredFocusedComponent(): JComponent? = phoneField

    override fun doValidate(): ValidationInfo? = loginError

    override fun doOKAction() {
        if (!okAction.isEnabled) {
            return
        }
        startLoading()

        loginJob = RaccoonClientManager.launchClientJob {
            kotlin.runCatching {
                passwordField!!.password.let { pwd ->
                    it.login(phoneField!!.text, pwd)
                    Arrays.fill(pwd, '0')
                }
            }.let { result ->
                rootPane.invokeOnUIThreadLater {
                    result.onSuccess {
                        close(OK_EXIT_CODE, true)
                    }.onFailure { e ->
                        if (e is CancellationException) {
                            close(CANCEL_EXIT_CODE, false)
                            throw e
                        } else {
                            stopLoading(e.localizedMessage)
                        }
                    }
                }
            }
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row(RaccoonBundle.message("login.dialog.label.phone")) {
            phoneField = textField().validationOnInput {
                if (PHONE_NUMBER_LENGTH != it.text.length) {
                    error(
                        RaccoonBundle.message(
                            "login.dialog.input.validation.length",
                            RaccoonBundle.message("login.dialog.label.phone"),
                            PHONE_NUMBER_LENGTH
                        )
                    )
                } else if (it.text.all { c -> c.isDigit() }) {
                    error(
                        RaccoonBundle.message(
                            "login.dialog.input.validation.onlyDigits",
                            RaccoonBundle.message("login.dialog.label.phone")
                        )
                    )
                } else {
                    null
                }
            }.component
        }
        row(RaccoonBundle.message("login.dialog.label.password")) {
            passwordField = passwordField().validationOnInput {
                val length = it.password.let { pwd ->
                    // Zero out the possible password, for security.
                    Arrays.fill(pwd, '0')
                    pwd.size
                }
                if (length < MIN_PASSWORD_LENGTH) error(
                    RaccoonBundle.message(
                        "login.dialog.input.validation.tooShort",
                        RaccoonBundle.message("login.dialog.label.password"),
                        MIN_PASSWORD_LENGTH
                    )
                ) else null
            }.comment(RaccoonBundle.message("login.dialog.text.forgotPassword", "todo")).component
        }
        row {
            text(RaccoonBundle.message("login.dialog.text.signup", "todo"))
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val PHONE_NUMBER_LENGTH = 11
    }
}