package com.sensetime.sensecore.sensecodeplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.*
import com.sensetime.sensecore.sensecodeplugin.messagebus.COMMON_ACTIONS_TOPIC
import com.sensetime.sensecore.sensecodeplugin.messagebus.CommonActions
import com.sensetime.sensecore.sensecodeplugin.openapi.RealOpenApi
import com.sensetime.sensecore.sensecodeplugin.ui.chat.ChatPanel
import com.sensetime.sensecore.sensecodeplugin.ui.history.HistoryPanel
import com.sensetime.sensecore.sensecodeplugin.ui.main.MainPresenter
import com.sensetime.sensecore.sensecodeplugin.ui.main.MainView
import com.sensetime.sensecore.sensecodeplugin.ui.main.Tab
import com.sensetime.sensecore.sensecodeplugin.ui.util.isInDarkMode
import org.intellij.lang.annotations.Language
import java.awt.BorderLayout
import java.awt.Desktop.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane

//todo Refactor in a proper GptMentorToolWindow class decoupled from the factory. See tool_window project
class GptMentorToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val presenter = MainPresenter()
        presenter.onAttach(project)

        val helpPane = JTextPane().apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            contentType = "text/html"
            isEditable = false
            addHyperlinkListener { e ->
                presenter.openUrl(e)
            }
        }

        val contentFactory = ContentFactory.SERVICE.getInstance()

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                println("selectioChanged")
                when (event.content?.displayName) {
                    Tab.CHAT.label -> {
                    }

                    Tab.HISTORY.label -> {
                        (event.content.component as HistoryPanel).presenter.refreshHistory()
                    }

                    Tab.HELP.label -> {
                        updateText(helpPane)
                    }

                    else -> {
                        // Do nothing
                    }
                }
            }
        })

        val chatPanel = ChatPanel(presenter).apply { onAttach(project) }
        val historyPanel = HistoryPanel { historyItem ->
            chatPanel.presenter.loadChatFromHistory(historyItem)
        }
        val helpPanel = createHelpPanel(helpPane)

        val chatContent = contentFactory.createContent(chatPanel, Tab.CHAT.label, false)
        toolWindow.contentManager.addContent(chatContent)

        val historyContent = contentFactory.createContent(historyPanel, Tab.HISTORY.label, false)
        historyPanel.presenter.refreshHistory()
        toolWindow.contentManager.addContent(historyContent)

        val helpContent = contentFactory.createContent(helpPanel, Tab.HELP.label, false)
        updateText(helpPane)
        toolWindow.contentManager.addContent(helpContent)

        project.messageBus.connect().subscribe(COMMON_ACTIONS_TOPIC, object : CommonActions {
            override fun selectTab(tab: Tab) {
                when (tab) {
                    Tab.CHAT -> {
                        toolWindow.contentManager.setSelectedContent(chatContent)
                    }

                    Tab.HISTORY -> {
                        toolWindow.contentManager.setSelectedContent(historyContent)
                    }

                    Tab.HELP -> {
                        toolWindow.contentManager.setSelectedContent(helpContent)
                    }
                }
            }
        })
    }

    private fun updateText(helpPane: JTextPane) {
        val helpTextColor = if (isInDarkMode()) {
            "#dddddd"
        } else {
            "#111111"
        }

        @Language("HTML")
        val helpText = """
                    <div style="font-family: Consolas; font-size: 12px; color: $helpTextColor;">
                        <h1 id="sensecode">SenseCode</h1>
                        <p>SenseCode 是基于商汤 SenseCore 大装置 及 SenseNova 日日新大模型而开发的代码助手。</p>
                        <h2 id="-">登录账号</h2>
                        <p>您可以在 IDE 设置界面中，选择 SenseCode 后在 <code>AccessKey ID</code> 和 <code>AccessKey Secret</code> 字段填入您<a href="https://console.sensecore.cn/iam/Security/access-key">商汤 SenseCore 大装置账号的AccessKey</a>相关信息即可。</p>
                        <blockquote>
                        <p>SenseCode 当前处于对特定企业用户邀约测试阶段，尚未对企业和个人用户全面开放。如您对 SenseCode 感兴趣，敬请发送邮件至 <a href="&#109;&#97;&#x69;&#x6c;&#116;&#x6f;&#58;&#115;&#101;&#x6e;&#115;&#101;&#99;&#x6f;&#100;&#x65;&#64;&#115;&#101;&#110;&#x73;&#101;&#x74;&#105;&#109;&#x65;&#46;&#x63;&#x6f;&#x6d;">&#115;&#101;&#x6e;&#115;&#101;&#99;&#x6f;&#100;&#x65;&#64;&#115;&#101;&#110;&#x73;&#101;&#x74;&#105;&#109;&#x65;&#46;&#x63;&#x6f;&#x6d;</a>，并留下您的企业或个人信息。</p>
                        </blockquote>
                        <h2 id="-">编辑器内代码补全</h2>
                        <p>您可以在编辑器内通过手动方式（默认热键为 <code>Ctrl + Alt + /</code> ）触发代码补全。自动方式开发中...</p>
                        <p>触发补全事件后，稍等片刻补全内容会逐步出现，您可以使用 <code>Tab</code> 键接受建议。</p>
                        <blockquote>
                        <p>编辑器在触发补全事件后，如遇用户点击 <code>Esc</code> 键的或其他移动、输入行为，则会 <strong>取消</strong> 补全操作。</p>
                        </blockquote>
                        <h2 id="-sensecode-">在编辑器内触发 SenseCode 命令</h2>
                        <p>你可以在编辑器内选择指定代码，然后单击右键选择 <code>SenseCode</code> 即可查看可以执行的命令， 选择想要执行的命令即可。</p>
                        <p>也可在选择指定代码后直接使用热键触发命令， 默认热键为</p>
                        <ul>
                        <li>Generation: <code>Ctrl + Alt + Shift + G</code></li>
                        <li>Add Test: <code>Ctrl + Alt + Shift + U</code></li>
                        <li>Code Correction: <code>Ctrl + Alt + Shift + F</code></li>
                        <li>Refactoring: <code>Ctrl + Alt + Shift + R</code></li>
                        </ul>
                        <h2 id="-sensecode-">在侧边栏助手内使用 SenseCode 命令</h2>
                        <p>你也可以在 SenseCode 的侧边栏中直接以对话形式向助手提问。</p>
                        <p>SenseCode 的侧边栏助手中，默认为 <code>流式输出</code> 模式，即回答将在产生过程中逐步显示，以便于您提早审阅结果，在这种模式下，您可以随时点击 <code>Stop</code> 按钮来终止此次回答。</p>
                        <h2 id="-">问题反馈</h2>
                        <p>如使用过程中遇到问题或有任何改进意见，欢迎发送邮件到 <a href="&#109;&#x61;&#105;&#108;&#116;&#x6f;&#x3a;&#x73;&#101;&#110;&#x73;&#x65;&#x63;&#x6f;&#100;&#101;&#64;&#x73;&#101;&#x6e;&#115;&#101;&#116;&#105;&#x6d;&#x65;&#46;&#x63;&#111;&#x6d;">&#x73;&#101;&#110;&#x73;&#x65;&#x63;&#x6f;&#100;&#101;&#64;&#x73;&#101;&#x6e;&#115;&#101;&#116;&#105;&#x6d;&#x65;&#46;&#x63;&#111;&#x6d;</a> 反馈。</p>
                        <h2 id="-">免责声明</h2>
                        <p>在使用商汤科技（“我方”）AI 代码助手及相关服务（以下简称 “本服务”）前，请您务必仔细阅读并理解透彻本《免责声明》。 请您知悉，如果您选择继续使用本服务，意味着您充分知悉并接受以下使用条件：</p>
                        <ul>
                        <li>您知悉并理解，本服务的输出内容及代码，为使用深度合成技术生成的文本信息，我们对其生成内容的准确性、完整性和功能性不做任何保证，并且其生成的内容不代表我们的态度或观点。</li>
                        <li>您理解并同意，本服务所为您展示的代码只是 “推荐”，若您选择采纳本服务所推荐的代码，应当视为您实际撰写了此代码，您应当是所产生、选择的代码的唯一著作权人。我方不会就本服务所推荐的任何代码承担安全、瑕疵、质量、兼容等任何保证责任，无论是明示或暗示，您有责任确保你生成的代码的安全和质量（无论其是由您完全自主撰写或者是采纳了本服务提出的建议），我们建议您在使用本服务推荐的代码时采取与使用您完全自主编写的代码时相同的预防措施。</li>
                        <li>您同意并承诺，不会使用本服务进行违反法律的应用开发，如您使用本服务开展特定行业的业务应用（如教育、医疗、银行行业），将同时遵守相关国家规定的用户数据保护法律和内容管理法律。</li>
                        <li>您确认并同意，我方不会因为本服务或您使用本服务违反上述约定，而需要承担任何责任。</li>
                        </ul>
                    </div>
                """.trimIndent()

        helpPane.text = helpText
    }

    private fun createHelpPanel(helpPane: JTextPane): JComponent {
        return JPanel().apply {
            layout = BorderLayout()
            val scrollPane = JBScrollPane(helpPane)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    companion object {
        const val ID = "SenseCode"
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RealOpenApi::class.java)
    }
}
