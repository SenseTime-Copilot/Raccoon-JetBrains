package com.sensetime.sensecore.sensecodeplugin.ui.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent

class LoadingButton(
    val button: JButton,
    val loading: JComponent,
    onClick: ((ActionEvent, () -> Unit) -> Unit)? = null
) {
    init {
        stopLoading()
        setOnClick(onClick)
        checkIsEnabled()
        button.addActionListener { e ->
            _onClick?.let {
                startLoading()
                it(e) {
                    ApplicationManager.getApplication()
                        .invokeLater({ stopLoading() }, ModalityState.stateForComponent(button))
                }
            }
        }
    }

    private var _onClick: ((ActionEvent, () -> Unit) -> Unit)? = null
    fun setOnClick(onClick: ((ActionEvent, () -> Unit) -> Unit)? = null) {
        if (_onClick !== onClick) {
            _onClick = onClick
            stopLoading()
            checkIsEnabled()
        }
    }

    private var _isEnabled: Boolean = true
    fun setIsEnabled(isEnabled: Boolean) {
        if (_isEnabled != isEnabled) {
            _isEnabled = isEnabled
            stopLoading()
            checkIsEnabled()
        }
    }

    private fun checkIsEnabled() {
        val check = _isEnabled && (null != _onClick)
        button.isEnabled = check
        loading.isEnabled = check
    }

    fun startLoading() {
        button.isVisible = false
        loading.isVisible = true
    }

    fun stopLoading() {
        loading.isVisible = false
        button.isVisible = true
    }
}