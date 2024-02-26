package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.ui.UIUtil
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.Job
import java.awt.Component
import java.util.*
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent
import kotlin.coroutines.cancellation.CancellationException

class LoginDialog(
    project: Project?, parent: Component?
) : DialogWrapper(
    project, parent, false, IdeModalityType.PROJECT
) {
    private var phoneNationCodeComboBox: ComboBox<String>? = null
    private var emailField: JBTextField? = null
    private var phoneField: JBTextField? = null
    private var passwordField: JBPasswordField? = null

    private var loginErrorEditorPane: JEditorPane? = null
    private var loginErrorText: String?
        get() = loginErrorEditorPane?.text
        set(value) {
            loginErrorEditorPane?.text =
                value?.letIfNotBlank { "<p style=\"color:${ColorUtil.toHex(UIUtil.getErrorForeground())};\">$it</p>" }
        }

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
        loginErrorText = null
    }

    private fun stopLoading(error: String? = null) {
        setOKButtonText(RaccoonBundle.message("authorization.panel.button.login"))
        isOKActionEnabled = true
        loginErrorText = error
    }

    override fun getPreferredFocusedComponent(): JComponent? = phoneField

    override fun doOKAction() {
        if (!okAction.isEnabled) {
            return
        }
        startLoading()

        loginJob = RaccoonClientManager.launchClientJob {
            kotlin.runCatching {
                passwordField!!.password.let { pwd ->
                    if (IS_TOB) {
                        it.login(emailField!!.text, pwd)
                    } else {
                        it.login(
                            (phoneNationCodeComboBox!!.selectedItem as String).trimStart('+'),
                            phoneField!!.text,
                            pwd
                        )
                    }
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
        if (IS_TOB) {
            row(RaccoonBundle.message("login.dialog.label.email")) {
                emailField = textField().validationOnApply {
                    val atIndex = it.text.indexOf('@')
                    val dotIndex = it.text.indexOf('.')
                    if ((atIndex < 1) || (dotIndex <= (atIndex + 1)) || (dotIndex >= it.text.lastIndex)) {
                        error(
                            RaccoonBundle.message(
                                "login.dialog.input.validation.invalid",
                                RaccoonBundle.message("login.dialog.label.email")
                            )
                        )
                    } else {
                        null
                    }
                }.horizontalAlign(HorizontalAlign.FILL).component.apply {
                    document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            loginErrorText = null
                        }
                    })
                }
            }
        } else {
            row(RaccoonBundle.message("login.dialog.label.phone")) {
                phoneNationCodeComboBox = comboBox(listOf("+86", "+852", "+853")).gap(RightGap.SMALL).component
                phoneField = textField().validationOnApply {
                    if (it.text.length !in MIN_PHONE_NUMBER_LENGTH..MAX_PHONE_NUMBER_LENGTH) {
                        error(
                            RaccoonBundle.message(
                                "login.dialog.input.validation.invalid",
                                RaccoonBundle.message("login.dialog.label.phone")
                            )
                        )
                    } else if (it.text.any { c -> !c.isDigit() }) {
                        error(
                            RaccoonBundle.message(
                                "login.dialog.input.validation.onlyDigits",
                                RaccoonBundle.message("login.dialog.label.phone")
                            )
                        )
                    } else {
                        null
                    }
                }.horizontalAlign(HorizontalAlign.FILL).component.apply {
                    document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            loginErrorText = null
                        }
                    })
                }
            }
        }

        row(RaccoonBundle.message("login.dialog.label.password")) {
            passwordField = cell(JBPasswordField()).validationOnApply {
                val length = it.password.let { pwd ->
                    // Zero out the possible password, for security.
                    Arrays.fill(pwd, '0')
                    pwd.size
                }
                if (length < MIN_PASSWORD_LENGTH) {
                    error(
                        RaccoonBundle.message(
                            "login.dialog.input.validation.tooShort",
                            RaccoonBundle.message("login.dialog.label.password"),
                            MIN_PASSWORD_LENGTH
                        )
                    )
                } else if (length > MAX_PASSWORD_LENGTH) {
                    error(
                        RaccoonBundle.message(
                            "login.dialog.input.validation.invalid",
                            RaccoonBundle.message("login.dialog.label.password")
                        )
                    )
                } else {
                    null
                }
            }.horizontalAlign(HorizontalAlign.FILL).component.apply {
                document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        loginErrorText = null
                    }
                })
            }
        }
        if (IS_TOB) {
            row {
                comment(
                    RaccoonBundle.message("login.dialog.text.forgotPassword.toB")
                ).horizontalAlign(HorizontalAlign.RIGHT)
            }
        } else {
            row {
                val loginBaseUrl = "${RaccoonClientManager.currentCodeClient.webBaseUrl!!}/login"
                val loginBaseParameters: Map<String, String> =
                    mapOf("utm_source" to "JetBrains ${RaccoonPlugin.ideName}")
                // trick for lang in <a> url will parse to %E2%8C%A9
                val loginUrlLang: String =
                    RaccoonBundle.message("login.dialog.link.web.lang").ifNullOrBlankElse("") { "&amp;lang=$it" }
                comment(
                    RaccoonBundle.message(
                        "login.dialog.text.signup",
                        newFromEncoded(loginBaseUrl).addParameters(loginBaseParameters).toExternalForm() + loginUrlLang
                    )
                )
                comment(
                    RaccoonBundle.message(
                        "login.dialog.text.forgotPassword",
                        newFromEncoded(loginBaseUrl).addParameters(
                            loginBaseParameters + Pair(
                                "step",
                                "forgot-password"
                            )
                        )
                            .toExternalForm() + loginUrlLang
                    )
                ).horizontalAlign(HorizontalAlign.RIGHT)
            }
        }
        row {
            loginErrorEditorPane = text("").component
        }
    }

    companion object {
        private const val IS_TOB = false
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 1024
        private const val MIN_PHONE_NUMBER_LENGTH = 6
        private const val MAX_PHONE_NUMBER_LENGTH = 32
    }
}