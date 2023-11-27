package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.responses.Usage
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonClientRequestStateListener
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import kotlinx.coroutines.*
import java.awt.event.MouseEvent
import javax.swing.Icon

class RaccoonStatusBarWidgetFactory : StatusBarWidgetFactory {
    companion object {
        const val ID = RaccoonPlugin.NAME
    }

    override fun isAvailable(project: Project): Boolean = true
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = ID

    override fun createWidget(project: Project): StatusBarWidget = RaccoonStatusBarWidget()

    private class RaccoonStatusBarWidget : StatusBarWidget, StatusBarWidget.IconPresentation,
        RaccoonClientRequestStateListener {
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
            clientMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
                it.subscribe(SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC, this)
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

        override fun onStart(id: Long) {
            RaccoonUIUtils.invokeOnUIThreadLater {
                currentID = id
                isRunning = true
                currentTooltipText = null
                currentIcon = AnimatedIcon.Default()
                myStatusBar?.updateWidget(ID)
            }
        }

        override fun onDone(id: Long, usage: Usage?) {
            RaccoonUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    isRunning = false
                    currentTooltipText = usage?.getShowString()
                    currentIcon =
                        if (null != usage?.completion?.takeIf { number -> number <= RaccoonSettingsState.instance.candidates }) RaccoonIcons.STATUS_BAR_EMPTY else RaccoonIcons.STATUS_BAR_SUCCESS
                    myStatusBar?.updateWidget(ID)
                }
            }
        }

        override fun onError(id: Long, error: String?) {
            RaccoonUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    isRunning = false
                    currentTooltipText = error
                    currentIcon = RaccoonIcons.STATUS_BAR_ERROR
                    myStatusBar?.updateWidget(ID)
                }
            }
        }

        override fun onFinally(id: Long) {
            RaccoonUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    if (isRunning) {
                        isRunning = false
                        currentTooltipText = null
                        currentIcon = RaccoonIcons.STATUS_BAR_DEFAULT
                        myStatusBar?.updateWidget(ID)
                    } else {
                        delayJob = statusBarCoroutineScope?.launch {
                            delay(5000)
                            RaccoonUIUtils.invokeOnUIThreadLater {
                                if ((id == currentID) && (!isRunning)) {
                                    currentTooltipText = null
                                    currentIcon = RaccoonIcons.STATUS_BAR_DEFAULT
                                    myStatusBar?.updateWidget(ID)
                                }
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
            currentTooltipText ?: RaccoonClientManager.currentCodeClient.run { "${name}: $userName" }

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            RaccoonUIUtils.showRaccoonSettings()
        }

        override fun ID(): String = ID

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    }
}