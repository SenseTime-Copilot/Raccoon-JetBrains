package com.sensetime.intellij.plugins.sensecode.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.sensetime.intellij.plugins.sensecode.clients.SenseCodeClientManager
import com.sensetime.intellij.plugins.sensecode.persistent.settings.ModelConfig
import com.sensetime.intellij.plugins.sensecode.persistent.settings.SenseCodeSettingsState
import com.sensetime.intellij.plugins.sensecode.resources.SenseCodeBundle
import com.sensetime.intellij.plugins.sensecode.ui.common.UserAuthorizationPanelBuilder
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodeActionUtils
import com.sensetime.intellij.plugins.sensecode.utils.SenseCodePlugin
import javax.swing.JComponent

class SenseCodeConfigurable : Configurable, Disposable {
    private val userAuthorizationPanel: DialogPanel =
        UserAuthorizationPanelBuilder().build(this, SenseCodeClientManager.currentCodeClient.getAkSkSettings())

    private val settingsPanel: DialogPanel = panel {
        row {
            cell(userAuthorizationPanel)
        }

        group(SenseCodeBundle.message("settings.group.InlineCompletion.title")) {
            var autoCompleteButton: Cell<JBRadioButton>? = null
            buttonsGroup {
                row(SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.label")) {
                    radioButton(
                        SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Manual"),
                        false
                    ).component.toolTipText = SenseCodeBundle.message(
                        "settings.group.InlineCompletion.TriggerMode.button.Manual.tooltip",
                        SenseCodeActionUtils.getShortcutText(ManualTriggerInlineCompletionAction::class)
                    )
                    autoCompleteButton = radioButton(
                        SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto"),
                        true
                    ).apply {
                        component.toolTipText =
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.tooltip")
                    }
                }
            }.bind(SenseCodeSettingsState.instance::isAutoCompleteMode)

            indent {
                buttonsGroup {
                    row {
                        radioButton(
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.ShortDelay"),
                            1000
                        )
                        radioButton(
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.LongDelay"),
                            3000
                        )
                    }
                }.bind(SenseCodeSettingsState.instance::autoCompleteDelayMs).visibleIf(autoCompleteButton!!.selected)
            }

            buttonsGroup {
                row(SenseCodeBundle.message("settings.group.InlineCompletion.CompletionPreference.label")) {
                    for (value in ModelConfig.CompletionPreference.values()) {
                        radioButton(SenseCodeBundle.message(value.key), value)
                    }
                }
            }.bind(SenseCodeSettingsState.instance::inlineCompletionPreference)

            buttonsGroup {
                row(SenseCodeBundle.message("settings.group.InlineCompletion.MaxCandidateNumber.label")) {
                    for (number in SenseCodeSettingsState.MIN_CANDIDATES..SenseCodeSettingsState.MAX_CANDIDATES) {
                        radioButton("$number", number).component.toolTipText = SenseCodeBundle.message(
                            "settings.group.InlineCompletion.MaxCandidateNumber.tooltip",
                            number
                        ) + if (number > 1) SenseCodeBundle.message("settings.group.InlineCompletion.MaxCandidateNumber.monolithic") else SenseCodeBundle.message(
                            "settings.group.InlineCompletion.MaxCandidateNumber.streaming"
                        )
                    }
                }
            }.bind(SenseCodeSettingsState.instance::candidates)
        }
    }

    override fun createComponent(): JComponent = settingsPanel

    override fun getDisplayName(): String = SenseCodePlugin.NAME

    override fun isModified(): Boolean = settingsPanel.isModified() || userAuthorizationPanel.isModified()

    override fun apply() {
        settingsPanel.apply()
        userAuthorizationPanel.apply()
    }

    override fun reset() {
        super.reset()
        settingsPanel.reset()
        userAuthorizationPanel.reset()
    }

    override fun dispose() {}

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(this)
    }
}