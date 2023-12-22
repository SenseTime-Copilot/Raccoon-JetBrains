package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.AssistantMessage
import com.sensetime.sensecode.jetbrains.raccoon.persistent.histories.ChatConversation
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonIcons
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.addMouseListenerWithDisposable
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonMarkdown
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
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

//fun JTextPane.updateMarkDownText(markdownText: String): JTextPane = apply {
//    text = RaccoonMarkdown.convertMarkdownToHtml(markdownText)
//}
//
//fun JTextPane.updateStyle(styleAttrs: SimpleAttributeSet): JTextPane = apply {
//    styledDocument.setParagraphAttributes(0, styledDocument.length, styleAttrs, false)
//}

//fun JTextPane.updateMarkDownTextAndStyle(markdownText: String, styleAttrs: SimpleAttributeSet): JTextPane = apply {
//    updateMarkDownText(markdownText)
//    updateStyle(styleAttrs)
//}

class ConversationPanel(
    parent: Disposable,
    project: Project?,
    conversation: ChatConversation,
    eventListener: EventListener? = null
) : JPanel(BorderLayout()), MouseListener by object : MouseAdapter() {}, Disposable {
    interface EventListener {
        fun onDelete(e: ActionEvent?)
        fun onMouseDoubleClicked(e: MouseEvent?) {}
    }

    private val deleteButton: JButton = RaccoonUIUtils.createIconButton(AllIcons.Actions.DeleteTag)
    var assistantMessagePane: MessagePanel? = null
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
//            val userBackgroundColor = ColorUtil.brighter(this@ConversationPanel.getBackground(), 3)
            add(createRoleBox(true, conversation.user.name, conversation.user.timestampMs, deleteButton).apply {
//                background = userBackgroundColor
//                isOpaque = false
            })
            add(MessagePanel(project, conversation.user.displayText, SimpleAttributeSet().also { attrs ->
                if (!conversation.user.displayText.contains('\n')) {
                    StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_RIGHT)
                }
                StyleConstants.setForeground(attrs, JBColor(Color(77, 111, 151), Color(115, 170, 212)))
            }, this@ConversationPanel.takeIf { null != eventListener }, this@ConversationPanel))
            assistantMessagePane = conversation.assistant?.let { assistantMessage ->
//                val assistantBackgroundColor = ColorUtil.darker(this@ConversationPanel.getBackground(), 2)
                add(createRoleBox(false, assistantMessage.name, assistantMessage.timestampMs).apply {
//                    background = assistantBackgroundColor
                })
                MessagePanel(
                    project,
                    assistantMessage.displayText,
                    updateAssistantAttributeSet(assistantMessage.generateState),
                    this@ConversationPanel.takeIf { null != eventListener },
                    this@ConversationPanel
                ).also { messagePanel ->
//                    messagePanel.background = assistantBackgroundColor
                    add(messagePanel)
                }
            }
            add(JSeparator())
            addMouseListenerWithDisposable(this@ConversationPanel, this@ConversationPanel)
        }, BorderLayout.CENTER)

        this.eventListener = eventListener
        Disposer.register(parent, this)
    }

    override fun dispose() {
        assistantMessagePane = null
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
                JLabel(if (isUser) RaccoonIcons.TOOLWINDOW_USER else RaccoonIcons.TOOLWINDOW_ASSISTANT),
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
        ): SimpleAttributeSet? = when (generateState) {
            AssistantMessage.GenerateState.STOPPED -> JBColor.GRAY
            AssistantMessage.GenerateState.ERROR -> JBColor.RED
            else -> JBColor(Color(103, 81, 111), Color(187, 134, 206))
        }?.let { foreground ->
            StyleConstants.setForeground(attrs, foreground)
            attrs
        }

//        @JvmStatic
//        fun createContentTextPane(
//            isUser: Boolean,
//            displayText: String = "",
//            generateState: AssistantMessage.GenerateState? = null
//        ): JTextPane = JTextPane().apply {
//            isEditable = false
//            contentType = "text/html"
//            border = BorderFactory.createEmptyBorder(10, 10, 10, 16)
//
//            // color and align
//            val attrs = SimpleAttributeSet()
//            if (isUser) {
//                if (!displayText.contains('\n')) {
//                    StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_RIGHT)
//                }
//                StyleConstants.setForeground(attrs, JBColor(Color(77, 111, 151), Color(115, 170, 212)))
//            } else {
//                StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_LEFT)
//                updateAssistantAttributeSet(generateState!!, attrs)
//            }
//            updateMarkDownTextAndStyle(displayText, attrs)
//
//            addHyperlinkListener { e ->
//                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
//                    BrowserUtil.browse(e.url.toURI())
//                }
//            }
//        }

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