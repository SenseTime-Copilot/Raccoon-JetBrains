package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*


internal abstract class LoadingPanel : JPanel(BorderLayout()) {
    abstract val button: JComponent
    protected abstract val loading: JComponent

    protected fun startLoading() {
        button.isVisible = false
        loading.isVisible = true
    }

    protected fun stopLoading() {
        loading.isVisible = false
        button.isVisible = true
    }

    protected fun build() {
        stopLoading()
        add(Box.createHorizontalBox().apply {
            add(button.apply {
                border = BorderFactory.createEmptyBorder()
            })
            add(loading.apply {
                border = BorderFactory.createEmptyBorder()
            })
        }, BorderLayout.CENTER)
//        minimumSize = button.minimumSize
//        preferredSize = button.preferredSize
    }
}

internal class LoadingButton(
    button: JButton,
    override val loading: JComponent,
    onClick: ((e: ActionEvent, onFinallyInsideEdt: () -> Unit) -> Unit)
) : LoadingPanel() {
    override val button: JComponent = button.apply {
        addActionListener { e ->
            startLoading()
            onClick(e, ::stopLoading)
        }
    }

    init {
        build()
    }
}

internal class LoadingActionButton(
    text: String, description: String, icon: Icon, actionPlace: String,
    override val loading: JComponent,
    onClick: ((e: AnActionEvent, onFinallyInsideEdt: () -> Unit) -> Unit)
) : LoadingPanel() {
    override val button: JComponent = object : AnAction(text, description, icon) {
        override fun actionPerformed(e: AnActionEvent) {
            startLoading()
            onClick(e, ::stopLoading)
        }
    }.let { anAction ->
        ActionButton(
            anAction,
            anAction.templatePresentation.clone(),
            actionPlace,
            ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE
        )
    }

    init {
        build()
    }
}
