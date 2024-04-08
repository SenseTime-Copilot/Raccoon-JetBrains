package com.sensetime.sensecode.jetbrains.raccoon.ui.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonConfigurable
import com.sensetime.sensecode.jetbrains.raccoon.utils.plusIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Dimension
import java.awt.event.FocusListener
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.ListModel
import javax.swing.event.ListDataListener
import kotlin.coroutines.CoroutineContext


internal fun Component.toCoroutineContext(): CoroutineContext = ModalityState.stateForComponent(this).asContextElement()

internal fun Component?.invokeOnEdtLater(block: () -> Unit) {
    RaccoonUIUtils.invokeOnEdtLater(this, block)
}

internal fun <R> Component?.invokeOnEdtSync(block: () -> R): R = RaccoonUIUtils.invokeOnEdtSync(this, block)


internal object RaccoonUIUtils {
    val SMALL_GAP_SIZE: Int
        get() = JBUI.scale(6)

    val DEFAULT_GAP_SIZE: Int
        get() = JBUI.scale(8)

    val MEDIUM_GAP_SIZE: Int
        get() = JBUI.scale(10)

    val BIG_GAP_SIZE: Int
        get() = JBUI.scale(16)

    @JvmStatic
    private inline fun <R> Component?.letIfModalityStateNotNullOrElse(
        onNotNull: (ModalityState) -> R, onNull: () -> R
    ): R = if (null != this) onNotNull(ModalityState.stateForComponent(this)) else onNull()

    @JvmStatic
    fun invokeOnEdtLater(component: Component? = null, block: () -> Unit) {
        ApplicationManager.getApplication().run {
            component.letIfModalityStateNotNullOrElse({ invokeLater(block, it) }) { invokeLater(block) }
        }
    }

    @JvmStatic
    fun <R> invokeOnEdtSync(component: Component? = null, block: () -> R): R =
        ApplicationManager.getApplication().run {
            var result: Result<R>? = null
            val blockWrapper: () -> Unit = { result = runCatching(block) }
            component.letIfModalityStateNotNullOrElse({
                invokeAndWait(blockWrapper, it)
            }) { invokeAndWait(blockWrapper) }
            result!!.getOrThrow()
        }

    @JvmStatic
    fun createActionLink(text: String = ""): JButton = ActionLink(text).apply {
        isFocusPainted = false
        autoHideOnDisable = false
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
    fun createActionLinkBiggerOn1(text: String = ""): JButton =
        createActionLink(text).apply { font = JBFont.label().biggerOn(1f) }
}


internal fun Component.addFocusListenerWithDisposable(parentDisposable: Disposable, listener: FocusListener) {
    addFocusListener(listener)
    Disposer.register(parentDisposable) { removeFocusListener(listener) }
}

internal fun Component.addKeyListenerWithDisposable(parentDisposable: Disposable, listener: KeyListener) {
    addKeyListener(listener)
    Disposer.register(parentDisposable) { removeKeyListener(listener) }
}

internal fun Component.addMouseListenerWithDisposable(parentDisposable: Disposable, listener: MouseListener) {
    addMouseListener(listener)
    Disposer.register(parentDisposable) { removeMouseListener(listener) }
}

internal fun ListModel<*>.addListDataListenerWithDisposable(parentDisposable: Disposable, listener: ListDataListener) {
    addListDataListener(listener)
    Disposer.register(parentDisposable) { removeListDataListener(listener) }
}
