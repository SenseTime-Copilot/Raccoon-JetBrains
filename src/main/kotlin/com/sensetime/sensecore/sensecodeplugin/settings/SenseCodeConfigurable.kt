package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClientManager
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import javax.swing.JComponent

class SenseCodeConfigurable : Configurable {
    private class SettingsComponent : Disposable {
        val settingsPanel: DialogPanel = panel {
            row {
                cell(
                    UserLoginPanel(
                        this@SettingsComponent,
                        CodeClientManager.getClientAndConfigPair().first.getAkSkSettings()
                    ).userLoginPanel
                )
            }

            group(SenseCodeBundle.message("settings.group.InlineCompletion.title")) {
                buttonsGroup {
                    row(SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.label")) {
                        radioButton(
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto"),
                            true
                        ).component.toolTipText =
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Auto.tooltip")
                        radioButton(
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Manual"),
                            false
                        ).component.toolTipText =
                            SenseCodeBundle.message("settings.group.InlineCompletion.TriggerMode.button.Manual.tooltip")
                    }
                }.bind(SenseCodeSettingsState.instance::isAutoCompleteMode)

                buttonsGroup {
                    row(SenseCodeBundle.message("settings.group.InlineCompletion.CompletionPreference.label")) {
                        for (value in ModelConfig.CompletionPreference.values()) {
                            radioButton(SenseCodeBundle.message(value.key), value)
                        }
                    }
                }.bind(SenseCodeSettingsState.instance::completionPreference)

                buttonsGroup {
                    row(SenseCodeBundle.message("settings.group.InlineCompletion.MaxCandidateNumber.label")) {
                        for (number in 1..3) {
                            radioButton("$number", number)
                        }
                    }
                }.bind(SenseCodeSettingsState.instance::candidates)
            }
        }

        override fun dispose() {}
    }

    private var settingsComponent: SettingsComponent? = null
        set(value) {
            field?.let { Disposer.dispose(it) }
            field = value
        }

    override fun createComponent(): JComponent {
        return SettingsComponent().let {
            settingsComponent = it
            it.settingsPanel
        }
    }

    override fun getDisplayName(): String = SenseCodePlugin.NAME
    override fun isModified(): Boolean = (true == settingsComponent?.settingsPanel?.isModified())

    override fun apply() {
        settingsComponent?.settingsPanel?.apply()
    }

    override fun reset() {
        super.reset()
        settingsComponent?.settingsPanel?.reset()
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        settingsComponent = null
    }
}