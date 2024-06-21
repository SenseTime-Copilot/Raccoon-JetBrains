package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render.GraphicsUtils
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonPromptSettings
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonActionUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import javax.swing.JComponent
import javax.swing.JPanel


internal class RaccoonConfigurable() : Configurable, Disposable {
    private val userAuthorizationPanel: JPanel = LLMClientManager.currentLLMClient.makeUserAuthorizationPanel(this)
    private val inlineCompletionColorPanel: ColorPanel = ColorPanel().apply {
        selectedColor = GraphicsUtils.niceContrastColor
    }
    private val settingsPanel: DialogPanel = panel {
        row {
            cell(userAuthorizationPanel)
        }

        group(RaccoonBundle.message("settings.group.InlineCompletion.title")) {
            var autoCompleteButton: Cell<JBRadioButton>? = null
            buttonsGroup {
                row(RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.label")) {
                    radioButton(
                        RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Manual"),
                        false
                    ).component.toolTipText = RaccoonBundle.message(
                        "settings.group.InlineCompletion.TriggerMode.button.Manual.tooltip",
                        RaccoonActionUtils.getShortcutText(ManualTriggerInlineCompletionAction::class)
                    )
                    autoCompleteButton = radioButton(
                        RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto"),
                        true
                    ).apply {
                        component.toolTipText =
                            RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.tooltip")
                    }
                }
            }.bind(RaccoonSettingsState.instance::isAutoCompleteMode)

            indent {
                row(RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.TriggerDelay")) {
                    val delaySpinner = spinner(
                        1000..60000,
                        1000
                    ).bindIntValue(RaccoonSettingsState.instance::autoCompleteDelayMs).component
                    label("(${RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.ms")})").gap(
                        RightGap.COLUMNS
                    )
                    button(RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.ShortDelay")) {
                        delaySpinner.number = 1000
                    }
                    button(RaccoonBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.LongDelay")) {
                        delaySpinner.number = 3000
                    }
                }
            }.visibleIf(autoCompleteButton!!.selected)

            buttonsGroup {
                row(RaccoonBundle.message("settings.group.InlineCompletion.CompletionPreference.label")) {
                    for (value in CompletionModelConfig.CompletionPreference.entries) {
                        radioButton(RaccoonBundle.message(value.key), value)
                    }
                }
            }.bind(RaccoonSettingsState.instance::inlineCompletionPreference)

            buttonsGroup {
                row(RaccoonBundle.message("settings.group.InlineCompletion.MaxCandidateNumber.label")) {
                    for (number in RaccoonSettingsState.MIN_CANDIDATES..RaccoonSettingsState.MAX_CANDIDATES) {
                        radioButton("$number", number).component.toolTipText = RaccoonBundle.message(
                            "settings.group.InlineCompletion.MaxCandidateNumber.tooltip",
                            number
                        ) + if (number > 1) RaccoonBundle.message("settings.group.InlineCompletion.MaxCandidateNumber.monolithic") else RaccoonBundle.message(
                            "settings.group.InlineCompletion.MaxCandidateNumber.streaming"
                        )
                    }
                }
            }.bind(RaccoonSettingsState.instance::candidates)

            row(RaccoonBundle.message("settings.group.InlineCompletion.ColorForCompletions.label")) {
                cell(inlineCompletionColorPanel)
            }
        }
        val isKnowledgeBaseAllowed = RaccoonClient.getIsKnowledgeBaseAllowed()
        if (RaccoonSettingsState.instance.isKnowledgeEnabled) {
            group(RaccoonBundle.message("settings.group.knowledgeBase.label.title")) {
//            row {
//                checkBox(RaccoonBundle.message("settings.group.knowledgeBase.checkBox.enableLocal")).bindSelected(
//                    RaccoonSettingsState.instance::isLocalKnowledgeBaseEnabled
//                ).enabled(RaccoonSettingsState.instance.isCloudKnowledgeBaseEnabled)
//            }
                row {
                    checkBox(RaccoonBundle.message("settings.group.knowledgeBase.checkBox.enableCloud")).bindSelected(
                        RaccoonSettingsState.instance::isCloudKnowledgeBaseEnabled
                    )
                }
            }
        }

        group(RaccoonBundle.message("settings.group.prompt.label.title")) {
            row {
                label("Git commit:")
                    .verticalAlign(VerticalAlign.TOP)
                    .gap(RightGap.SMALL)
                textArea()
                    .rows(7)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .bindText(RaccoonPromptSettings.getInstance()::commitPromptTemplate)
                    .comment(
                        RaccoonBundle.message("settings.group.prompt.gitcommit.comment"),
                        MAX_LINE_LENGTH_WORD_WRAP
                    )
            }
        }
    }

    override fun createComponent(): JComponent = settingsPanel

    override fun getDisplayName(): String = RaccoonPlugin.name

    private fun isInlineCompletionColorModified(): Boolean =
        GraphicsUtils.niceContrastColor != inlineCompletionColorPanel.selectedColor

    override fun isModified(): Boolean =
        settingsPanel.isModified() || isInlineCompletionColorModified()

    override fun apply() {
        settingsPanel.apply()
        if (isInlineCompletionColorModified()) {
            RaccoonSettingsState.instance.inlineCompletionColor =
                ColorUtil.toHex(inlineCompletionColorPanel.selectedColor!!)
        }
    }

    override fun reset() {
        super.reset()
        settingsPanel.reset()
        inlineCompletionColorPanel.selectedColor = GraphicsUtils.niceContrastColor
    }

    override fun dispose() {}

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(this)
    }
}
