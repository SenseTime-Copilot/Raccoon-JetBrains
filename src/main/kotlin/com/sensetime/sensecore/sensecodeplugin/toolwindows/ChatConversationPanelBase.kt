package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.markdown.utils.convertMarkdownToHtml
import java.awt.BorderLayout
import javax.swing.*

abstract class ChatConversationPanelBase : JPanel() {
    private var assistantJTextPane: JTextPane? = null

    init {
        layout = BorderLayout()
    }

    protected fun build(
        conversation: SenseCodeChatHistoryState.Conversation,
        deleteButton: JButton?
    ): ChatConversationPanelBase {
        add(Box.createVerticalBox().apply {
            add(JSeparator())
            add(
                Utils.createMessagePanel(
                    Utils.createRoleBox(
                        true,
                        conversation.user.datetime,
                        conversation.user.displayName,
                        deleteButton
                    ),
                    Utils.createContentTextPane(
                        true,
                        SenseCodeChatHistoryState.GenerateState.DONE,
                        conversation.user.displayText
                    )
                )
            )
            Utils.createContentTextPane(false, conversation.generateState, conversation.assistant.displayText).let {
                add(
                    Utils.createMessagePanel(
                        Utils.createRoleBox(
                            false,
                            conversation.assistant.datetime,
                            conversation.assistant.displayName
                        ), it
                    )
                )
                assistantJTextPane = it
            }
            add(JSeparator())
        }, BorderLayout.CENTER)

        return this
    }

    fun setAssistantText(text: String, generateState: SenseCodeChatHistoryState.GenerateState) {
        assistantJTextPane?.text = convertMarkdownToHtml(text)
        assistantJTextPane?.styledDocument?.run {
            setParagraphAttributes(0, length, Utils.createAssistantForegroundAttributeSet(generateState), false)
        }
    }
}