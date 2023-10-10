package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.ide.BrowserUtil
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.clients.SenseNovaClient
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

object Utils {
    @JvmStatic
    fun getCurrentDatetimeString(): String = SimpleDateFormat("yyyy/MM/dd, HH:mm:ss").format(Date())

    @JvmStatic
    fun createRoleBox(
        isUser: Boolean, datetime: String,
        displayName: String, deleteButton: JButton? = null
    ): Box = Box.createHorizontalBox().apply {
        val components: List<Component> = listOfNotNull(
            Box.createHorizontalStrut(16),
            JLabel(if (isUser) SenseCodeIcons.TOOLWINDOW_USER else SenseCodeIcons.TOOLWINDOW_ASSISTANT),
            Box.createHorizontalStrut(8),
            Box.createVerticalBox().apply {
                val labelAlignmentX = if (isUser) Component.RIGHT_ALIGNMENT else Component.LEFT_ALIGNMENT
                add(JLabel(displayName).apply {
                    font = JBFont.h4()
                    alignmentX = labelAlignmentX
                })
                add(JLabel(datetime).apply {
                    font = JBFont.small()
                    alignmentX = labelAlignmentX
                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                })
            },
            Box.createHorizontalGlue(),
            deleteButton,
            Box.createHorizontalStrut(8)
        )
        (if (isUser) components.asReversed() else components).forEach { add(it) }
    }

    @JvmStatic
    fun createAssistantForegroundAttributeSet(
        generateState: SenseCodeChatHistoryState.GenerateState,
        attrs: SimpleAttributeSet = SimpleAttributeSet()
    ): SimpleAttributeSet {
        val foreground = when (generateState) {
            SenseCodeChatHistoryState.GenerateState.ONLY_PROMPT, SenseCodeChatHistoryState.GenerateState.DONE -> JBColor(
                Color(103, 81, 111),
                Color(187, 134, 206)
            )

            SenseCodeChatHistoryState.GenerateState.STOPPED -> JBColor.GRAY
            SenseCodeChatHistoryState.GenerateState.ERROR -> JBColor.RED
        }
        StyleConstants.setForeground(attrs, foreground)
        return attrs
    }

    @JvmStatic
    fun createContentTextPane(
        isUser: Boolean,
        generateState: SenseCodeChatHistoryState.GenerateState,
        displayText: String = ""
    ): JTextPane = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
        text = convertMarkdownToHtml(displayText)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // color and align
        val attrs = SimpleAttributeSet()
        if (isUser) {
            StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_RIGHT)
            StyleConstants.setForeground(attrs, JBColor(Color(77, 111, 151), Color(115, 170, 212)))
        } else {
            StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_LEFT)
            createAssistantForegroundAttributeSet(generateState, attrs)
        }
        styledDocument.setParagraphAttributes(0, styledDocument.length, attrs, false)

        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.url.toURI())
            }
        }
    }

    @JvmStatic
    fun createMessagePanel(
        roleBox: Box,
        contentTextPane: JTextPane
    ): JPanel = JPanel().apply {
        layout = BorderLayout(0, 2)
        add(roleBox, BorderLayout.NORTH)
        add(contentTextPane, BorderLayout.CENTER)
    }

    @JvmStatic
    fun toCodeRequestMessage(conversations: List<SenseCodeChatHistoryState.Conversation>): List<CodeRequest.Message> {
        val result: List<CodeRequest.Message> = listOfNotNull(
            if (SenseNovaClient.CLIENT_NAME != CodeClientManager.getClientAndConfigPair().second.name) CodeRequest.Message(
                "system",
                ""
            ) else null
        )

        return result + conversations.flatMap { conversation ->
            when (conversation.generateState) {
                SenseCodeChatHistoryState.GenerateState.ONLY_PROMPT -> listOf(
                    CodeRequest.Message(
                        "user",
                        conversation.user.getPromptString()
                    )
                )

                SenseCodeChatHistoryState.GenerateState.DONE -> listOf(
                    CodeRequest.Message(
                        "user",
                        conversation.user.getPromptString()
                    ), CodeRequest.Message("assistant", conversation.assistant.getPromptString())
                )

                SenseCodeChatHistoryState.GenerateState.STOPPED, SenseCodeChatHistoryState.GenerateState.ERROR -> listOf()
            }
        }
    }
}