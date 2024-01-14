package com.sensetime.sensecode.jetbrains.raccoon.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render.GraphicsUtils
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.common.UserAuthorizationPanelBuilder
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonActionUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import javax.swing.JComponent

class RaccoonConfigurable : Configurable, Disposable {
    private val userAuthorizationPanel: DialogPanel =
        UserAuthorizationPanelBuilder().build(this, RaccoonClientManager.currentCodeClient.getAkSkSettings())
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
                    for (value in ModelConfig.CompletionPreference.values()) {
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
    }

    override fun createComponent(): JComponent = settingsPanel

    override fun getDisplayName(): String = RaccoonPlugin.NAME

    private fun isInlineCompletionColorModified(): Boolean =
        GraphicsUtils.niceContrastColor != inlineCompletionColorPanel.selectedColor

    override fun isModified(): Boolean =
        settingsPanel.isModified() || userAuthorizationPanel.isModified() || isInlineCompletionColorModified()

    override fun apply() {
        settingsPanel.apply()
        userAuthorizationPanel.apply()
        if (isInlineCompletionColorModified()) {
            RaccoonSettingsState.instance.inlineCompletionColor =
                ColorUtil.toHex(inlineCompletionColorPanel.selectedColor!!)
        }
    }

    override fun reset() {
        super.reset()
        settingsPanel.reset()
        userAuthorizationPanel.reset()
        inlineCompletionColorPanel.selectedColor = GraphicsUtils.niceContrastColor
    }

    override fun dispose() {}

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(this)
    }
}