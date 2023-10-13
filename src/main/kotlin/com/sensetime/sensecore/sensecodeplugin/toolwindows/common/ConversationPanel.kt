package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

fun JTextPane.updateMarkDownText(markdownText: String): JTextPane = apply {
    text = convertMarkdownToHtml(markdownText)
}

fun JTextPane.updateStyle(styleAttrs: SimpleAttributeSet): JTextPane = apply {
    styledDocument.setParagraphAttributes(0, styledDocument.length, styleAttrs, false)
}

fun JTextPane.updateMarkDownTextAndStyle(markdownText: String, styleAttrs: SimpleAttributeSet): JTextPane = apply {
    text = convertMarkdownToHtml(markdownText)
    styledDocument.setParagraphAttributes(0, styledDocument.length, styleAttrs, false)
}

class ConversationPanel : JPanel(BorderLayout()), Disposable {
    interface EventListener {
        fun onDelete(e: ActionEvent?)
    }

    private val deleteButton: JButton = ButtonUtils.createIconButton(AllIcons.Actions.DeleteTag)
    var assistantJTextPane: JTextPane? = null
        private set

    var conversation: ChatConversation? = null
        set(value) {
            add(Box.createVerticalBox().apply {
                value?.let {
                    add(JSeparator())
                    add(createRoleBox(true, it.name, it.user.timestampMs, deleteButton))
                    add(createContentTextPane(true, ChatConversation.State.DONE, it.user.content))
                    assistantJTextPane = it.assistant?.let { assistantMessage ->
                        add(createRoleBox(false, SenseCodePlugin.NAME, assistantMessage.timestampMs))
                        createContentTextPane(false, it.state, assistantMessage.content).also { assistantPane ->
                            add(assistantPane)
                        }
                    }
                    add(JSeparator())
                }
            }, BorderLayout.CENTER)
            isVisible = (null != value)
            field = value
        }

    var eventListener: EventListener? = null
        set(value) {
            if (field !== value) {
                field?.let { deleteButton.removeActionListener(it::onDelete) }
                value?.let { deleteButton.addActionListener(it::onDelete) }
                deleteButton.isVisible = (null != value)

                field = value
            }
        }

    fun build(
        parent: Disposable,
        conversation: ChatConversation? = null,
        eventListener: EventListener? = null
    ): ConversationPanel = apply {
        this.eventListener = eventListener
        this.conversation = conversation
        Disposer.register(parent, this)
    }

    override fun dispose() {
        assistantJTextPane = null
        conversation = null
        eventListener = null
    }

    companion object {
        @JvmStatic
        fun getDisplayDatetime(timestampMs: Long): String =
            SimpleDateFormat("yyyy/MM/dd, HH:mm:ss").format(Date(timestampMs))

        @JvmStatic
        fun createRoleBox(
            isUser: Boolean, name: String, timestampMs: Long, deleteButton: JButton? = null
        ): Box = Box.createHorizontalBox().apply {
            val components: List<Component> = listOfNotNull(
                Box.createHorizontalStrut(16),
                JLabel(if (isUser) SenseCodeIcons.TOOLWINDOW_USER else SenseCodeIcons.TOOLWINDOW_ASSISTANT),
                Box.createHorizontalStrut(8),
                Box.createVerticalBox().apply {
                    val labelAlignmentX = if (isUser) Component.RIGHT_ALIGNMENT else Component.LEFT_ALIGNMENT
                    add(JLabel(name).apply {
                        font = JBFont.h4()
                        alignmentX = labelAlignmentX
                    })
                    add(JLabel(getDisplayDatetime(timestampMs)).apply {
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
        fun updateAssistantAttributeSet(
            state: ChatConversation.State,
            attrs: SimpleAttributeSet = SimpleAttributeSet()
        ): SimpleAttributeSet = attrs.also {
            val foreground = when (state) {
                ChatConversation.State.STOPPED -> JBColor.GRAY
                ChatConversation.State.ERROR -> JBColor.RED
                else -> JBColor(Color(103, 81, 111), Color(187, 134, 206))
            }
            StyleConstants.setForeground(it, foreground)
        }

        @JvmStatic
        fun createContentTextPane(
            isUser: Boolean,
            state: ChatConversation.State,
            displayText: String = ""
        ): JTextPane = JTextPane().apply {
            isEditable = false
            contentType = "text/html"
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // color and align
            val attrs = SimpleAttributeSet()
            if (isUser) {
                if (!displayText.contains('\n')) {
                    StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_RIGHT)
                }
                StyleConstants.setForeground(attrs, JBColor(Color(77, 111, 151), Color(115, 170, 212)))
            } else {
                StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_LEFT)
                updateAssistantAttributeSet(state, attrs)
            }
            updateMarkDownTextAndStyle(displayText, attrs)

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
    }
}