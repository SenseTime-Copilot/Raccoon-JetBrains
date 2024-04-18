package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.ui.UIUtil
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonCredentialsManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.takeIfNotEmpty
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.ifNullOrBlankElse
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank
import kotlinx.coroutines.Job
import java.awt.Component
import java.awt.Dimension
import java.util.*
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent


internal class LoginDialog(
    private val project: Project?, parent: Component?,
    private val webLoginUrl: String?, private val webForgotPasswordUrl: String?
) : DialogWrapper(
    project, parent, false, IdeModalityType.PROJECT
) {
    private var emailField: JBTextField? = null
    private var phoneField: JBTextField? = null
    private var passwordField: JBPasswordField? = null
    private var phoneNationCodeComboBox: ComboBox<String>? = null
    private var savePasswordCheckBox: JBCheckBox? = null

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

        loginJob = LLMClientManager.launchClientJob { llmClient ->
            RaccoonExceptions.resultOf {
                val raccoonClient = llmClient as RaccoonClient
                val pwd = passwordField!!.password
                val user = if (RaccoonConfig.config.isToB()) emailField!!.text else phoneField!!.text
                RaccoonCredentialsManager.setLoginInfo(
                    LLMClientManager.currentLLMClient.name,
                    if (true == savePasswordCheckBox?.isSelected) Credentials(user, pwd) else null
                )
                if (RaccoonConfig.config.isToB()) {
                    raccoonClient.loginWithEmail(project, contentPanel, emailField!!.text, pwd)
                } else {
                    raccoonClient.loginWithPhone(
                        project, contentPanel,
                        (phoneNationCodeComboBox!!.selectedItem as String).trimStart('+'),
                        phoneField!!.text,
                        pwd
                    )
                }
                Arrays.fill(pwd, '0')
            }.onSuccess { close(OK_EXIT_CODE, true) }.onFailure { e -> stopLoading(e.localizedMessage) }
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        val loginInfo = RaccoonCredentialsManager.getLoginInfo(LLMClientManager.currentLLMClient.name)?.takeIfNotEmpty()
        if (RaccoonConfig.config.isToB()) {
            row(RaccoonBundle.message("login.dialog.label.email")) {
                emailField = textField().validationOnApply {
                    val atIndex = it.text.indexOf('@')
                    if (atIndex !in 1..<it.text.lastIndex) {
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
                    loginInfo?.let {
                        text = it.userName
                    }
                    document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            loginErrorText = null
                        }
                    })
                    // larger for show email
                    minimumSize = Dimension((preferredSize.width * 1.5).toInt(), minimumSize.height)
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
                    loginInfo?.let {
                        text = it.userName
                    }
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
                loginInfo?.let {
                    text = it.getPasswordAsString()
                }
                document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        loginErrorText = null
                    }
                })
            }
        }
        row {
            savePasswordCheckBox =
                checkBox(RaccoonBundle.message("login.dialog.checkbox.savePassword")).component.apply {
                    isSelected = (null != loginInfo)
                }
        }
        if (RaccoonConfig.config.isToB()) {
            row {
                comment(
                    RaccoonBundle.message("login.dialog.text.forgotPassword.toB")
                ).horizontalAlign(HorizontalAlign.RIGHT)
            }
        } else {
            row {
                val loginBaseParameters: Map<String, String> =
                    mapOf("utm_source" to "JetBrains ${RaccoonPlugin.ideName}")
                // trick for lang in <a> url will parse to %E2%8C%A9
                val loginUrlLang: String =
                    RaccoonBundle.message("login.dialog.link.web.lang").ifNullOrBlankElse("") { "&amp;lang=$it" }

                webLoginUrl?.letIfNotBlank {
                    comment(
                        RaccoonBundle.message(
                            "login.dialog.text.signup",
                            newFromEncoded(it).addParameters(loginBaseParameters).toExternalForm() + loginUrlLang
                        )
                    )
                }
                webForgotPasswordUrl?.letIfNotBlank {
                    comment(
                        RaccoonBundle.message(
                            "login.dialog.text.forgotPassword",
                            newFromEncoded(it).addParameters(loginBaseParameters).toExternalForm() + loginUrlLang
                        )
                    ).horizontalAlign(HorizontalAlign.RIGHT)
                }
            }
        }
        row {
            loginErrorEditorPane = text("").component
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 1024
        private const val MIN_PHONE_NUMBER_LENGTH = 6
        private const val MAX_PHONE_NUMBER_LENGTH = 32
    }
}