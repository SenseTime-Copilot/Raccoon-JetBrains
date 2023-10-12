package com.sensetime.sensecore.sensecodeplugin.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.clients.responses.Usage
import com.sensetime.sensecore.sensecodeplugin.messages.SENSE_CODE_CLIENTS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messages.SenseCodeClientsListener
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import kotlinx.coroutines.*
import javax.swing.Icon

class SenseCodeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SenseCodePlugin.STATUS_BAR_ID

    override fun getDisplayName(): String = SenseCodePlugin.STATUS_BAR_ID

    override fun createWidget(project: Project): StatusBarWidget = SenseCodeStatusBarWidget()

    private class SenseCodeStatusBarWidget : StatusBarWidget, StatusBarWidget.IconPresentation {
        private var isRunning: Boolean = false
        private var myStatusBar: StatusBar? = null
        private var currentTooltipText: String? = null
        private var currentIcon: Icon = SenseCodeIcons.STATUS_BAR_DEFAULT
        private var statusBarCoroutineScope: CoroutineScope? =
            CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseCodeStatusBar"))
        private var clientMessageBusConnection: SimpleMessageBusConnection? = null
            set(value) {
                field?.disconnect()
                field = value
            }

        init {
            clientMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
                it.subscribe(SENSE_CODE_CLIENTS_TOPIC, object : SenseCodeClientsListener {
                    override fun onStart() {
                        ApplicationManager.getApplication().invokeLater {
                            isRunning = true
                            currentTooltipText = null
                            currentIcon = AnimatedIcon.Default()
                            myStatusBar?.updateWidget(SenseCodePlugin.STATUS_BAR_ID)
                        }
                    }

                    override fun onDone(usage: Usage?) {
                        ApplicationManager.getApplication().invokeLater {
                            isRunning = false
                            currentTooltipText = usage?.getShowString()
                            currentIcon =
                                if (null != usage?.completion?.takeIf { number -> number <= 1 }) SenseCodeIcons.STATUS_BAR_EMPTY else SenseCodeIcons.STATUS_BAR_SUCCESS
                            myStatusBar?.updateWidget(SenseCodePlugin.STATUS_BAR_ID)
                        }
                    }

                    override fun onError(error: String?) {
                        ApplicationManager.getApplication().invokeLater {
                            isRunning = false
                            currentTooltipText = error
                            currentIcon = SenseCodeIcons.STATUS_BAR_ERROR
                            myStatusBar?.updateWidget(SenseCodePlugin.STATUS_BAR_ID)
                        }
                    }

                    override fun onFinally() {
                        ApplicationManager.getApplication().invokeLater {
                            if (isRunning) {
                                isRunning = false
                                currentTooltipText = null
                                currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT
                                myStatusBar?.updateWidget(SenseCodePlugin.STATUS_BAR_ID)
                            }
                            statusBarCoroutineScope?.launch {
                                delay(5000)
                                ApplicationManager.getApplication().invokeLater {
                                    if (!isRunning) {
                                        currentTooltipText = null
                                        currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT
                                        myStatusBar?.updateWidget(SenseCodePlugin.STATUS_BAR_ID)
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }

        override fun install(statusBar: StatusBar) {
            super.install(statusBar)
            myStatusBar = statusBar
        }

        override fun dispose() {
            super.dispose()
            isRunning = false
            currentTooltipText = null
            myStatusBar = null
            currentIcon = SenseCodeIcons.STATUS_BAR_DEFAULT
            clientMessageBusConnection = null
            statusBarCoroutineScope?.cancel()
            statusBarCoroutineScope = null
        }

        override fun getIcon(): Icon = currentIcon

        override fun getTooltipText(): String =
            currentTooltipText ?: CodeClientManager.getClientAndConfigPair().run { "${second.name}: ${first.userName}" }

        override fun ID(): String = SenseCodePlugin.STATUS_BAR_ID

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    }
}