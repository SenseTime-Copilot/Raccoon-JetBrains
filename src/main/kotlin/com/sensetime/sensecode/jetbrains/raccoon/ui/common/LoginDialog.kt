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
                    it.login((phoneNationCodeComboBox!!.selectedItem as String).trimStart('+'), phoneField!!.text, pwd)
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
            phoneNationCodeComboBox = comboBox(listOf("+86", "+852", "+853")).gap(RightGap.SMALL).component
            phoneField = textField().validationOnApply {
                if (PHONE_NUMBER_LENGTH != it.text.length) {
                    error(
                        RaccoonBundle.message(
                            "login.dialog.input.validation.length",
                            RaccoonBundle.message("login.dialog.label.phone"),
                            PHONE_NUMBER_LENGTH
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
        row(RaccoonBundle.message("login.dialog.label.password")) {
            passwordField = cell(JBPasswordField()).validationOnApply {
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
            }.horizontalAlign(HorizontalAlign.FILL).component.apply {
                document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        loginErrorText = null
                    }
                })
            }
        }
        row {
            val webBaseUrl: String = RaccoonClientManager.currentCodeClient.webBaseUrl!!
            comment(
                RaccoonBundle.message(
                    "login.dialog.text.signup",
                    newFromEncoded("$webBaseUrl/register").addParameters(mapOf("utm_source" to "JetBrains ${RaccoonPlugin.ideName}"))
                        .toExternalForm()
                )
            )
            comment(
                RaccoonBundle.message(
                    "login.dialog.text.forgotPassword",
                    "$webBaseUrl/reset-password"
                )
            ).horizontalAlign(HorizontalAlign.RIGHT)
        }
        row {
            loginErrorEditorPane = text("").component
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val PHONE_NUMBER_LENGTH = 11
    }
}