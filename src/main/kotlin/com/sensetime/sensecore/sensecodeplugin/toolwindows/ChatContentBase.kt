package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.toolwindows.common.Utils
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.reflect.KProperty0

abstract class ChatContentBase