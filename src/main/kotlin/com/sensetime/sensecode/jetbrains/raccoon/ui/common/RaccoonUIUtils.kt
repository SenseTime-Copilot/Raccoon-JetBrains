package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonConfigurable
import java.awt.Component
import java.awt.Dimension
import java.awt.event.FocusListener
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.ListModel
import javax.swing.event.ListDataListener

fun Component.invokeOnUIThreadLater(block: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(block, ModalityState.stateForComponent(this))
}

fun Component.addFocusListenerWithDisposable(parentDisposable: Disposable, listener: FocusListener) {
    addFocusListener(listener)
    Disposer.register(parentDisposable) { removeFocusListener(listener) }
}

fun Component.addKeyListenerWithDisposable(parentDisposable: Disposable, listener: KeyListener) {
    addKeyListener(listener)
    Disposer.register(parentDisposable) { removeKeyListener(listener) }
}

fun Component.addMouseListenerWithDisposable(parentDisposable: Disposable, listener: MouseListener) {
    addMouseListener(listener)
    Disposer.register(parentDisposable) { removeMouseListener(listener) }
}

fun ListModel<*>.addListDataListenerWithDisposable(parentDisposable: Disposable, listener: ListDataListener) {
    addListDataListener(listener)
    Disposer.register(parentDisposable) { removeListDataListener(listener) }
}

object RaccoonUIUtils {
    val SMALL_GAP_SIZE: Int
        get() = JBUI.scale(6)

    val DEFAULT_GAP_SIZE: Int
        get() = JBUI.scale(8)

    val MEDIUM_GAP_SIZE: Int
        get() = JBUI.scale(10)

    val BIG_GAP_SIZE: Int
        get() = JBUI.scale(16)

    @JvmStatic
    fun invokeOnUIThreadLater(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(block)
    }

    @JvmStatic
    fun showRaccoonSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, RaccoonConfigurable::class.java)
    }

    @JvmStatic
    fun createIconButton(icon: Icon): JButton = JButton(icon).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
    }

    @JvmStatic
    fun createActionLink(text: String = ""): JButton = ActionLink(text).apply {
        isFocusPainted = false
        autoHideOnDisable = false
    }

    @JvmStatic
    fun createActionLinkBiggerOn1(text: String = ""): JButton =
        createActionLink(text).apply { font = JBFont.label().biggerOn(1f) }
}