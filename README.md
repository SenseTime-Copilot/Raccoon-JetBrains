# Raccoon

<!-- Plugin description -->
## Raccoon (**R**accoon is **A**nother **C**ode **CO**-pil**O**t **N**avigator)

[Raccoon](https://code.sensetime.com?utm_source=JetBrains%20IntelliJ%20IDEA) is your AI coding assistant. It provides real-time code completions, code assistant, as well as actions for `Generation`, `Add Test`, `Code Conversion`, `Code Correction` and `Refactoring`. It helps increase your development velocity and enhances your coding experience.

[Raccoon](https://code.sensetime.com?utm_source=JetBrains%20IntelliJ%20IDEA)，是基于 AI 的代码助手。提供编辑器内代码补全、侧边栏代码助手以及`代码生成`、`测试代码生成`、`代码翻译`、`代码修正`、`代码重构`等命令。可以提升用户编程效率优化开发体验。

## 账号管理/设置

您可以在 Raccoon 代码助手 侧边栏视图或设置界面上方，点击 `Log in` / `登录` 按钮在弹出的对话框使用 手机号 + 密码 进行登录，点击 `Log out` / `登出` 按钮退出登录

> 如果您还没有账号或忘记密码，可以点击登录对话框下方链接跳转到网页端进行注册或重置密码操作

在 Raccoon 设置页面中（通过点击 IDE 状态栏右下的图标，可以快速进入设置页面），可以配置编辑器内代码补全的

* 触发方式：
  * 手动：当按下快捷键时获取补全建议，默认的快捷键为 `Ctrl + Alt + /`（mac: `⌃⌥/`）
  * 自动：当输入停止时获取补全建议，您可以在设置页面切换延迟时间的长短，此时手动触发仍然有效
* 补全偏好：
  * 行级补全：返回单行的补全建议
  * 平衡：兼顾响应速度和补全建议长度
  * 最大长度：由模型在最大允许范围内自行确定返回内容
* 候选建议数量：
  * 设置返回候选条目的数量
* 代码补全的显示颜色：

在 IDE 的快捷键设置页面，您可以修改本插件的所有快捷键映射

![Raccoon-Settings](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-Settings.gif)

## 代码补全

您可以在编辑器内通过手动或自动方式触发代码补全，一经触发，编辑器状态栏右下的图标将指示现在的请求状态。

触发补全事件后，稍等片刻补全内容会以行内补全候选框形式出现，您可以使用 `Tab` 键接受建议。候选数量大于 1，可以使用 `Alt + [` 及 `Alt + ]`（mac: `⌥[` 及 `⌥]`）来进行翻页浏览，确定要接受的建议项后，使用 `Tab` 键插入编辑器。

> 编辑器在触发补全事件后，如遇用户点击 `Esc` 键的或其他移动、输入行为，则会 **取消** 补全操作。

![Raccoon-InlineCompletion](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-InlineCompletion.gif)

## 代码命令

你可以在编辑器内选择指定代码，然后单击右键选择 ` Raccoon ` 即可查看可以执行的命令， 选择想要执行的命令即可。

也可在选择指定代码后直接使用快捷键触发命令， 默认快捷键为

* Generation: `Ctrl + Alt + Shift + G`（mac: `⌃⌥⇧G`）
* Add Test: `Ctrl + Alt + Shift + U`（mac: `⌃⌥⇧U`）
* Code Conversion: `Ctrl + Alt + Shift + X`（mac: `⌃⌥⇧X`）
* Code Correction: `Ctrl + Alt + Shift + F`（mac: `⌃⌥⇧F`）
* Refactoring: `Ctrl + Alt + Shift + R`（mac: `⌃⌥⇧R`）

在选中代码的状态下，使用快捷键 `Ctrl + Alt + /` （与手动触发代码补全相同，mac: `⌃⌥/`），可以弹出代码命令列表便于您快速选择使用。

![Raccoon-Refactoring](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-Refactoring.gif)
![Raccoon-CodeCorrection](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-CodeCorrection.gif)

## 撰写提交信息

如当前项目是一个 Git 仓库，且 IDE 已经启用自带的 Git 插件，在 `Commit` 页面，会看到 `魔棒图标` 按钮，Raccoon 会理解您已选择的更改，并在消息框中撰写合适的 Commit Message 建议。

![Raccoon-GitCommitMessage](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-GitCommitMessage.gif)

## 侧边栏助手

你也可以在 Raccoon 的侧边栏中直接以对话形式向助手提问。
如在编辑器内有活动的代码文件内容被选中，则会随问题一起发送。

Raccoon 的侧边栏助手中，默认为 `流式输出` 模式，即回答将在产生过程中逐步显示，以便于您提早审阅结果，在这种模式下，您可以随时点击 `Stop` 按钮来终止此次回答。

对于对话中可识别的代码内容，插件将以代码框形式显示，并提供 `自动换行`、 `复制到剪贴板` 和 `插入到当前光标`等便捷按钮, 便于您的查看和操作。

### 关于上下文

在提问过程中，Raccoon 会按照算法 token 长度要求，携带合适数量的上下文信息一起发送。当有部分历史对话消息影响回答效果时，您可以通过点击对应问答条目左上角的 `×` 手动将其移除后再试。

或者您可以点击 Raccoon 侧边栏低部的 `New Chat` / `新建对话` 按钮来重新开始一个空白的对话。原有对话内容会被自动保存到历史，可以在历史页面双击恢复。

帮助信息和错误提示等非对话内容，不会作为上下文发送。

![Raccoon-Chat](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-Chat.gif)
![Raccoon-SelectCode](https://raw.githubusercontent.com/SenseTime-Copilot/Raccoon-JetBrains/v0.9/media/Raccoon-SelectCode.gif)

## 免责声明

在使用 Raccoon（“我方”）产品及相关服务（以下简称 “本服务”）前，请您务必仔细阅读并理解透彻本《免责声明》。 请您知悉，如果您选择继续使用本服务，意味着您充分知悉并接受以下使用条件：

* 您知悉并理解，本服务的输出内容及代码，为使用深度合成技术生成的文本信息，我们对其生成内容的准确性、完整性和功能性不做任何保证，并且其生成的内容不代表我们的态度或观点。
* 您理解并同意，本服务所为您展示的代码只是 “推荐”，若您选择采纳本服务所推荐的代码，应当视为您实际撰写了此代码，您应当是所产生、选择的代码的唯一著作权人。我方不会就本服务所推荐的任何代码承担安全、瑕疵、质量、兼容等任何保证责任，无论是明示或暗示，您有责任确保你生成的代码的安全和质量（无论其是由您完全自主撰写或者是采纳了本服务提出的建议），我们建议您在使用本服务推荐的代码时采取与使用您完全自主编写的代码时相同的预防措施。
* 您同意并承诺，不会使用本服务进行违反法律的应用开发，如您使用本服务开展特定行业的业务应用（如教育、医疗、银行行业），将同时遵守相关国家规定的用户数据保护法律和内容管理法律。
* 您确认并同意，我方不会因为本服务或您使用本服务违反上述约定，而需要承担任何责任。

<!-- Plugin description end -->
