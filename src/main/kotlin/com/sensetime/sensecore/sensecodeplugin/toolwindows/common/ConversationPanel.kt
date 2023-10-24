package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeIcons
import com.sensetime.sensecore.sensecodeplugin.settings.ModelConfig
import com.sensetime.sensecore.sensecodeplugin.ui.common.ButtonUtils
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeMarkdown
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
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

class ConversationPanel : JPanel(BorderLayout()), MouseListener by object : MouseAdapter() {}, Disposable {
    interface EventListener {
        fun onDelete(e: ActionEvent?)
        fun onMouseDoubleClicked(e: MouseEvent?) {}
    }

    private val deleteButton: JButton = ButtonUtils.createIconButton(AllIcons.Actions.DeleteTag)
    var assistantTextPane: JTextPane? = null
        private set
    private var promptTemplate: ModelConfig.PromptTemplate? = null
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
        add(Box.createVerticalBox().apply {
            conversation?.let {
                val prompt = CodeClientManager.getClientAndConfigPair().second.getModelConfigByType(it.type)
                    .getPromptTemplateByType(it.type)
                add(JSeparator())
                add(createRoleBox(true, it.name, it.user.timestampMs, deleteButton))
                add(
                    createContentTextPane(
                        true,
                        ChatConversation.State.DONE,
                        prompt.getUserPromptDisplay(it.user.args)
                    ).apply {
                        addMouseListener(this@ConversationPanel, this@ConversationPanel)
                    }
                )
                assistantTextPane = it.assistant?.let { assistantMessage ->
                    add(createRoleBox(false, SenseCodePlugin.NAME, assistantMessage.timestampMs))
                    createContentTextPane(
                        false,
                        it.state,
                        prompt.getAssistantTextDisplay(assistantMessage.args)
                    ).also { assistantPane ->
                        add(assistantPane)
                        assistantPane.addMouseListener(this@ConversationPanel, this@ConversationPanel)
                    }
                }
                add(JSeparator())
                promptTemplate = prompt
            }
            addMouseListener(this@ConversationPanel, this@ConversationPanel)
        }, BorderLayout.CENTER)

        this.eventListener = eventListener
        Disposer.register(parent, this)
    }

    override fun dispose() {
        assistantTextPane = null
        eventListener = null
        promptTemplate = null
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