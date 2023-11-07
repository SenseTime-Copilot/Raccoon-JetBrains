package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.intellij.plugins.sensecode.persistent.histories.AssistantMessage
import com.sensetime.intellij.plugins.sensecode.persistent.histories.ChatConversation
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeIcons
import com.sensetime.intellij.plugins.sensecode.ui.common.SenseCodeUIUtils
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeMarkdown
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

fun JTextPane.updateMarkDownText(markdownText: String): JTextPane = apply {
    text = SenseCodeMarkdown.convertMarkdownToHtml(markdownText)
}

fun JTextPane.updateStyle(styleAttrs: SimpleAttributeSet): JTextPane = apply {
    styledDocument.setParagraphAttributes(0, styledDocument.length, styleAttrs, false)
}

fun JTextPane.updateMarkDownTextAndStyle(markdownText: String, styleAttrs: SimpleAttributeSet): JTextPane = apply {
    updateMarkDownText(markdownText)
    updateStyle(styleAttrs)
}

class ConversationPanel(
    parent: Disposable,
    conversation: ChatConversation,
    eventListener: EventListener? = null
) : JPanel(BorderLayout()), MouseListener by object : MouseAdapter() {}, Disposable {
    interface EventListener {
        fun onDelete(e: ActionEvent?)
        fun onMouseDoubleClicked(e: MouseEvent?) {}
    }

    private val deleteButton: JButton = SenseCodeUIUtils.createIconButton(AllIcons.Actions.DeleteTag)
    var assistantTextPane: JTextPane? = null
        private set
    private var eventListener: EventListener? = null
        set(value) {
            if (field !== value) {
                field?.let { deleteButton.removeActionListener(it::onDelete) }
                value?.let { deleteButton.addActionListener(it::onDelete) }
                deleteButton.isVisible = (null != value)

                field = value
            }
        }

    init {
        add(Box.createVerticalBox().apply {
            add(JSeparator())
            add(createRoleBox(true, conversation.user.name, conversation.user.timestampMs, deleteButton))
            add(
                createContentTextPane(
                    true,
                    conversation.user.displayText
                ).apply { addMouseListener(this@ConversationPanel, this@ConversationPanel) })
            assistantTextPane = conversation.assistant?.let { assistantMessage ->
                add(createRoleBox(false, assistantMessage.name, assistantMessage.timestampMs))
                createContentTextPane(
                    false,
                    assistantMessage.displayText,
                    assistantMessage.generateState
                ).also { assistantPane ->
                    add(assistantPane)
                    assistantPane.addMouseListener(this@ConversationPanel, this@ConversationPanel)
                }
            }
            add(JSeparator())
            addMouseListener(this@ConversationPanel, this@ConversationPanel)
        }, BorderLayout.CENTER)

        this.eventListener = eventListener
        Disposer.register(parent, this)
    }

    override fun dispose() {
        assistantTextPane = null
        eventListener = null
    }

    override fun mouseClicked(e: MouseEvent?) {
        e?.takeIf { 2 == it.clickCount }?.let {
            eventListener?.onMouseDoubleClicked(e)
        }
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
            generateState: AssistantMessage.GenerateState,
            attrs: SimpleAttributeSet = SimpleAttributeSet()
        ): SimpleAttributeSet = attrs.also {
            val foreground = when (generateState) {
                AssistantMessage.GenerateState.STOPPED -> JBColor.GRAY
                AssistantMessage.GenerateState.ERROR -> JBColor.RED
                else -> JBColor(Color(103, 81, 111), Color(187, 134, 206))
            }
            StyleConstants.setForeground(it, foreground)
        }

        @JvmStatic
        fun createContentTextPane(
            isUser: Boolean,
            displayText: String = "",
            generateState: AssistantMessage.GenerateState? = null
        ): JTextPane = JTextPane().apply {
            isEditable = false
            contentType = "text/html"
            border = BorderFactory.createEmptyBorder(10, 10, 10, 16)

            // color and align
            val attrs = SimpleAttributeSet()
            if (isUser) {
                if (!displayText.contains('\n')) {
                    StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_RIGHT)
                }
                StyleConstants.setForeground(attrs, JBColor(Color(77, 111, 151), Color(115, 170, 212)))
            } else {
                StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_LEFT)
                updateAssistantAttributeSet(generateState!!, attrs)
            }
            updateMarkDownTextAndStyle(displayText, attrs)

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    BrowserUtil.browse(e.url.toURI())
                }
            }
        }

//        @JvmStatic
//        fun createMessagePanel(
//            roleBox: Box,
//            contentTextPane: JTextPane
//        ): JPanel = JPanel().apply {
//            layout = BorderLayout(0, 2)
//            add(roleBox, BorderLayout.NORTH)
//            add(contentTextPane, BorderLayout.CENTER)
//        }
    }
}