package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBFont
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonConfigurable
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton

fun Component.invokeOnUIThreadLater(block: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(block, ModalityState.stateForComponent(this))
}

object RaccoonUIUtils {
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