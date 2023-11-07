package com.sensetime.intellij.plugins.sensecode.ui.toolwindows

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBScrollPane
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeMarkdown
import org.intellij.lang.annotations.Language
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent

class HelpContentPanel : JPanel() {
    init {
        layout = BorderLayout()

        add(
            JBScrollPane(JTextPane().apply {
                isEditable = false
                contentType = "text/html"
                text = SenseCodeMarkdown.convertMarkdownToHtml(MARKDOWN_HELP)
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                addHyperlinkListener { e ->
                    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        BrowserUtil.browse(e.url.toURI())
                    }
                }
            }, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
            BorderLayout.CENTER
        )
    }

    companion object {
        @Language("Markdown")
        private const val MARKDOWN_HELP: String = """
# SenseCode

SenseCode 是基于商汤 [SenseNova](https://www.sensenova.cn/) 日日新大模型而开发的代码助手。

## 登录账号

您可以在 IDE 侧边栏或设置界面上方，点击 `Log In` / `登录` 按钮通过商量网页登录。

> SenseCode 当前处于对特定企业用户邀约测试阶段，尚未对企业和个人用户全面开放。如您对 SenseCode 感兴趣，敬请发送邮件至 <sensecode@sensetime.com>，并留下您的企业或个人信息。

## 编辑器内代码补全

您可以在编辑器内通过手动方式（默认快捷键为 `Ctrl + Alt + /` ）触发代码补全，一经触发，编辑器状态栏右下的 `⧉` 图标将指示现在的请求状态。自动方式开发中...

在设置页面中，可以配置编辑器内代码补全的

* 补全偏好：
    * 速度优先：优先保证响应速度，返回简短的补全建议
    * 平衡：兼顾响应速度和补全建议长度
    * 最大长度：最大可能的返回尽可能长的补全建议
* 候选建议数量：
    * 设置返回候选条目的数量

触发补全事件后，稍等片刻补全内容会以行内补全候选框形式出现，您可以使用 `Tab` 键接受建议。候选数量大于 1, 可以使用 `Alt + [` 及 `Alt + ]` 来进行翻页浏览，确定要接受的建议项后，使用 `Tab` 键插入编辑器。

> 编辑器在触发补全事件后，如遇用户点击 `Esc` 键的或其他移动、输入行为，则会 **取消** 补全操作。

## 在编辑器内触发 SenseCode 命令

你可以在编辑器内选择指定代码，然后单击右键选择 ` SenseCode ` 即可查看可以执行的命令， 选择想要执行的命令即可。

也可在选择指定代码后直接使用快捷键触发命令， 默认快捷键为

- Generation: `Ctrl + Alt + Shift + G`
- Add Test: `Ctrl + Alt + Shift + U`
- Code Conversion: `Ctrl + Alt + Shift + X`
- Code Correction: `Ctrl + Alt + Shift + F`
- Refactoring: `Ctrl + Alt + Shift + R`

## 在侧边栏助手内使用 SenseCode 命令

你也可以在 SenseCode 的侧边栏中直接以对话形式向助手提问。

SenseCode 的侧边栏助手中，默认为 `流式输出` 模式，即回答将在产生过程中逐步显示，以便于您提早审阅结果，在这种模式下，您可以随时点击 `Stop` 按钮来终止此次回答。

## 问题反馈

如使用过程中遇到问题或有任何改进意见，欢迎发送邮件到 <sensecode@sensetime.com> 反馈。

## 免责声明

在使用商汤科技（“我方”）AI 代码助手及相关服务（以下简称 “本服务”）前，请您务必仔细阅读并理解透彻本《免责声明》。 请您知悉，如果您选择继续使用本服务，意味着您充分知悉并接受以下使用条件：

* 您知悉并理解，本服务的输出内容及代码，为使用深度合成技术生成的文本信息，我们对其生成内容的准确性、完整性和功能性不做任何保证，并且其生成的内容不代表我们的态度或观点。
* 您理解并同意，本服务所为您展示的代码只是 “推荐”，若您选择采纳本服务所推荐的代码，应当视为您实际撰写了此代码，您应当是所产生、选择的代码的唯一著作权人。我方不会就本服务所推荐的任何代码承担安全、瑕疵、质量、兼容等任何保证责任，无论是明示或暗示，您有责任确保你生成的代码的安全和质量（无论其是由您完全自主撰写或者是采纳了本服务提出的建议），我们建议您在使用本服务推荐的代码时采取与使用您完全自主编写的代码时相同的预防措施。
* 您同意并承诺，不会使用本服务进行违反法律的应用开发，如您使用本服务开展特定行业的业务应用（如教育、医疗、银行行业），将同时遵守相关国家规定的用户数据保护法律和内容管理法律。
* 您确认并同意，我方不会因为本服务或您使用本服务违反上述约定，而需要承担任何责任。
"""
    }
}