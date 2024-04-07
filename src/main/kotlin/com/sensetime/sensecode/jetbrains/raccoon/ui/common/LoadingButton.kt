package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel


class LoadingButton(
    private val button: JButton,
    private val loading: JComponent,
    onClick: ((e: ActionEvent?, onFinallyInsideEdt: () -> Unit) -> Unit)
) : JPanel() {
    private fun startLoading() {
        button.isVisible = false
        loading.isVisible = true
    }

    private fun stopLoading() {
        loading.isVisible = false
        button.isVisible = true
    }

    init {
        stopLoading()
        add(button)
        add(loading)
        button.addActionListener { e ->
            startLoading()
            onClick(e, ::stopLoading)
        }
    }
}
