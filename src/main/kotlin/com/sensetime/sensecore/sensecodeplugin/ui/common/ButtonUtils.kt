package com.sensetime.sensecore.sensecodeplugin.ui.common

import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBFont
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton

object ButtonUtils {
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