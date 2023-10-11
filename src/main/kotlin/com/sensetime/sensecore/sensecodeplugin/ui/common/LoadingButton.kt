package com.sensetime.sensecore.sensecodeplugin.ui.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent

class LoadingButton(
    parent: Disposable,
    val button: JButton,
    val loading: JComponent,
    private var onClick: ((ActionEvent?, () -> Unit) -> Unit)? = null
) : Disposable, ActionListener {
    var isEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                stopLoading()
                checkIsEnabled()
            }
        }

    init {
        stopLoading()
        checkIsEnabled()
        button.addActionListener(this)
        Disposer.register(parent, this)
    }

    override fun actionPerformed(e: ActionEvent?) {
        onClick?.let {
            startLoading()
            it(e) {
                ApplicationManager.getApplication()
                    .invokeLater({ stopLoading() }, ModalityState.stateForComponent(button))
            }
        }
    }

    override fun dispose() {
        stopLoading()
        button.removeActionListener(this)
        onClick = null
    }

    private fun startLoading() {
        button.isVisible = false
        loading.isVisible = true
    }

    private fun stopLoading() {
        loading.isVisible = false
        button.isVisible = true
    }

    private fun checkIsEnabled() {
        val check = isEnabled && (null != onClick)
        button.isEnabled = check
        loading.isEnabled = check
    }
}