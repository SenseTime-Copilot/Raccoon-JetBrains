package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal class CopyToClipboardAction(private val editor: Editor, private val language: String) : AnAction(
    RaccoonBundle.message("codes.actions.copy.name"),
    RaccoonBundle.message("codes.actions.copy.description"),
    AllIcons.Actions.Copy
) {
    override fun actionPerformed(e: AnActionEvent) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(editor.document.text), null)
        ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
            .onToolWindowCodeCopied(language)
    }
}