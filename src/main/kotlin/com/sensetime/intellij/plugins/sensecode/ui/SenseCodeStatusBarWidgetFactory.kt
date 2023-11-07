package com.sensetime.intellij.plugins.sensecode.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.clients.responses.Usage
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeIcons
import com.sensetime.intellij.plugins.sensecode.topics.SENSE_CODE_CLIENT_REQUEST_STATE_TOPIC
import com.sensetime.intellij.plugins.sensecode.topics.SenseCodeClientRequestStateListener
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin
import kotlinx.coroutines.*
import java.awt.event.MouseEvent
import javax.swing.Icon

class SenseCodeStatusBarWidgetFactory : StatusBarWidgetFactory {
    companion object {
        const val ID = SenseCodePlugin.NAME
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = ID

    override fun createWidget(project: Project): StatusBarWidget = SenseCodeStatusBarWidget()

    private class SenseCodeStatusBarWidget : StatusBarWidget, StatusBarWidget.IconPresentation,
        SenseCodeClientRequestStateListener {
        private var myStatusBar: StatusBar? = null
        private var currentTooltipText: String? = null
        private var currentIcon: Icon = SenseCodeIcons.STATUS_BAR_DEFAULT

        private var statusBarCoroutineScope: CoroutineScope? =
            CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseCodeStatusBar"))
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
            super.dispose()
            myStatusBar = null
            currentTooltipText = null
            currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT

            isRunning = false
            currentID = -1
            clientMessageBusConnection = null
            statusBarCoroutineScope = null
        }

        override fun onStart(id: Long) {
            SenseCodeUIUtils.invokeOnUIThreadLater {
                currentID = id
                isRunning = true
                currentTooltipText = null
                currentIcon = AnimatedIcon.Default()
                myStatusBar?.updateWidget(ID)
            }
        }

        override fun onDone(id: Long, usage: Usage?) {
            SenseCodeUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    isRunning = false
                    currentTooltipText = usage?.getShowString()
                    currentIcon =
                        if (null != usage?.completion?.takeIf { number -> number <= SenseCodeSettingsState.instance.candidates }) SenseCodeIcons.STATUS_BAR_EMPTY else SenseCodeIcons.STATUS_BAR_SUCCESS
                    myStatusBar?.updateWidget(ID)
                }
            }
        }

        override fun onError(id: Long, error: String?) {
            SenseCodeUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    isRunning = false
                    currentTooltipText = error
                    currentIcon = SenseCodeIcons.STATUS_BAR_ERROR
                    myStatusBar?.updateWidget(ID)
                }
            }
        }

        override fun onFinally(id: Long) {
            SenseCodeUIUtils.invokeOnUIThreadLater {
                if (id == currentID) {
                    if (isRunning) {
                        isRunning = false
                        currentTooltipText = null
                        currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT
                        myStatusBar?.updateWidget(ID)
                    } else {
                        delayJob = statusBarCoroutineScope?.launch {
                            delay(5000)
                            SenseCodeUIUtils.invokeOnUIThreadLater {
                                if ((id == currentID) && (!isRunning)) {
                                    currentTooltipText = null
                                    currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT
                                    myStatusBar?.updateWidget(ID)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun install(statusBar: StatusBar) {
            super.install(statusBar)
            myStatusBar = statusBar
        }

        override fun getIcon(): Icon = currentIcon

        override fun getTooltipText(): String =
            currentTooltipText ?: SenseCodeClientManager.currentCodeClient.run { "${name}: $userName" }

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            SenseCodeUIUtils.showSenseCodeSettings()
        }

        override fun ID(): String = ID

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    }
}