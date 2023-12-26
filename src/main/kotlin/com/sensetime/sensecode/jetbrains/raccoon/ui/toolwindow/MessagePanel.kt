package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.addMouseListenerWithDisposable
import com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes.CodeEditorPanel
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonLanguages
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonMarkdown
import okhttp3.internal.indexOfFirstNonAsciiWhitespace
import java.awt.BorderLayout
import java.awt.event.MouseListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.SimpleAttributeSet

class MessagePanel(
    private val project: Project?,
    markdownText: String? = null,
    styleAttrs: SimpleAttributeSet? = null,
    private val parentDisposable: Disposable? = null, private val listener: MouseListener? = null
) : JPanel(BorderLayout()) {
    private val messageBox: Box = Box.createVerticalBox()
    private var lastRawText: String = ""
    private var lastTextPane: JTextPane? = null

    init {
        markdownText?.let { appendMarkdownText(it) }
        styleAttrs?.let { updateStyle(it) }
        add(messageBox, BorderLayout.CENTER)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 16)
    }

    fun updateStyle(styleAttrs: SimpleAttributeSet) {
        messageBox.components.mapNotNull { it as? JTextPane }
            .forEach { it.styledDocument.apply { setParagraphAttributes(0, length, styleAttrs, false) } }
    }

    fun appendMarkdownText(deltaMarkdownText: String) {
        if (deltaMarkdownText.isEmpty()) {
            return
        }

        val currentCodeIndex = deltaMarkdownText.findMarkdownCodeIndex()
        if (currentCodeIndex < 0) {
            appendToLastTextPane(deltaMarkdownText)
            return
        }
        val currentCodeEndIndex = deltaMarkdownText.indexOfFirstNonAsciiWhitespace(currentCodeIndex + 3)
        lastTextPane?.let { textPane ->
            lastRawText.findMarkdownCodeIndex().takeIf { it >= 0 }?.let { prevCodeIndex ->
                textPane.text = lastRawText.substring(0, prevCodeIndex).let {
                    if (it.isBlank()) {
                        it
                    } else {
                        RaccoonMarkdown.convertMarkdownToHtml(it)
                    }
                }
                if (prevCodeIndex <= 0) {
                    messageBox.remove(textPane)
                }
                addCodeEditorPane(
                    lastRawText.substring(prevCodeIndex + 3) + deltaMarkdownText.substring(
                        0,
                        currentCodeIndex
                    )
                )
            }
        } ?: deltaMarkdownText.run {
            appendToLastTextPane(substring(0, currentCodeEndIndex))
        }
        appendMarkdownText(deltaMarkdownText.substring(currentCodeEndIndex))
    }

    private fun addCodeEditorPane(codeBlockText: String) {
        var language = ""
        var code = codeBlockText
        codeBlockText.indexOf('\n').takeIf { it >= 0 }?.let {
            language = codeBlockText.substring(0, it).trim()
            code = codeBlockText.substring(it + 1)
        }
        messageBox.add(
            CodeEditorPanel(
                project,
                code,
                RaccoonLanguages.getLanguageFromMarkdownLanguage(language)
            ).apply {
                parentDisposable?.let {
                    addMouseListenerWithDisposable(it, listener!!)
                }
            })
        lastTextPane = null
        lastRawText = ""
    }

    private fun appendToLastTextPane(deltaMarkdownText: String) {
        if (null == lastTextPane) {
            lastTextPane = JTextPane().apply {
                isEditable = false
                contentType = "text/html"
                addHyperlinkListener { e ->
                    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        BrowserUtil.browse(e.url.toURI())
                    }
                }
                parentDisposable?.let {
                    addMouseListenerWithDisposable(it, listener!!)
                }
            }
            messageBox.add(lastTextPane)
        }
        lastTextPane!!.apply {
            lastRawText += deltaMarkdownText
            text = if (lastRawText.isBlank()) lastRawText else RaccoonMarkdown.convertMarkdownToHtml(lastRawText)
        }
    }

    companion object {
        @JvmStatic
        private fun String.findMarkdownCodeIndex(): Int = indexOfAny(listOf("```", "~~~"))
    }
}