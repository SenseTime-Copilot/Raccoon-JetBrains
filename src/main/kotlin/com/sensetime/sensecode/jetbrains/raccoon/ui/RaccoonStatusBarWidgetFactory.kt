package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import kotlinx.coroutines.*
import java.awt.event.MouseEvent
import javax.swing.Icon


internal class RaccoonStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun isAvailable(project: Project): Boolean = true
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun getId(): String = RaccoonPlugin.name

    override fun getDisplayName(): String = RaccoonPlugin.name

    override fun createWidget(project: Project): StatusBarWidget = RaccoonStatusBarWidget(project)

    private class RaccoonStatusBarWidget(
        project: Project
    ) : StatusBarWidget, StatusBarWidget.IconPresentation, RaccoonRequestStateListener {
        private var myStatusBar: StatusBar? = null
        private var currentTooltipText: String? = null
        private var currentIcon: Icon = RaccoonIcons.STATUS_BAR_DEFAULT

        private var statusBarCoroutineScope: CoroutineScope? =
            CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("RaccoonStatusBar"))
            set(value) {
                field?.cancel()
                field = value
            }
        private var delayJob: Job? = null
            set(value) {
                field?.cancel()
                field = value
            }

        private var isRunning: Boolean = false
        private var currentID: Long = -1
            set(value) {
                delayJob = null
                field = value
            }

        private var clientMessageBusConnection: SimpleMessageBusConnection? = null
            set(value) {
                field?.disconnect()
                field = value
            }

        init {
            clientMessageBusConnection = project.messageBus.connect().also {
                it.subscribe(RACCOON_REQUEST_STATE_TOPIC, this)
            }
        }

        override fun dispose() {
            myStatusBar = null
            currentTooltipText = null
            currentIcon = RaccoonIcons.STATUS_BAR_DEFAULT

            isRunning = false
            currentID = -1
            clientMessageBusConnection = null
            statusBarCoroutineScope = null
        }

        override fun onStartInsideEdtAndCatching(id: Long, action: String?) {
            currentID = id
            isRunning = true
            currentTooltipText = null
            currentIcon = AnimatedIcon.Default()
            myStatusBar?.updateWidget(RaccoonPlugin.name)
        }

        override fun onDoneInsideEdtAndCatching(id: Long, message: String?) {
            if (id == currentID) {
                isRunning = false
                currentTooltipText = message
                currentIcon = if (null == message) RaccoonIcons.STATUS_BAR_EMPTY else RaccoonIcons.STATUS_BAR_SUCCESS
                myStatusBar?.updateWidget(RaccoonPlugin.name)
            }
        }

        override fun onFailureIncludeCancellationInsideEdtAndCatching(id: Long, t: Throwable) {
            if (id == currentID && (t !is CancellationException)) {
                isRunning = false
                currentTooltipText = t.localizedMessage
                currentIcon = RaccoonIcons.STATUS_BAR_ERROR
                myStatusBar?.updateWidget(RaccoonPlugin.name)
            }
        }

        override fun onFinallyInsideEdtAndCatching(id: Long) {
            if (id == currentID) {
                if (isRunning) {
                    isRunning = false
                    currentTooltipText = null
                    currentIcon = RaccoonIcons.STATUS_BAR_DEFAULT
                    myStatusBar?.updateWidget(RaccoonPlugin.name)
                } else {
                    delayJob = statusBarCoroutineScope?.launch {
                        delay(5000)
                        RaccoonUIUtils.invokeOnEdtLater {
                            if ((id == currentID) && (!isRunning)) {
                                currentTooltipText = null
                                currentIcon = RaccoonIcons.STATUS_BAR_DEFAULT
                                myStatusBar?.updateWidget(RaccoonPlugin.name)
                            }
                        }
                    }
                }
            }
        }

        override fun install(statusBar: StatusBar) {
            myStatusBar = statusBar
        }

        override fun getIcon(): Icon = currentIcon

        override fun getTooltipText(): String =
            currentTooltipText ?: "${LLMClientManager.currentLLMClient.name}: ${
                RaccoonUserInformation.getInstance().getDisplayUserName()
            }"

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            RaccoonUIUtils.showRaccoonSettings()
        }

        override fun ID(): String = RaccoonPlugin.name

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    }
}
