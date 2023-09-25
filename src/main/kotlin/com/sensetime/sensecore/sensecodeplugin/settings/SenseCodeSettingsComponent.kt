package com.sensetime.sensecore.sensecodeplugin.settings

import ai.grazie.utils.applyIfNotNull
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import com.sensetime.sensecore.sensecodeplugin.ui.common.UserLoginPanel
import java.awt.event.ActionEvent

internal fun Panel.akskPasswordRow(item: CodeClient.AkSkSettingsItem): Row =
    row(item.label) {
        passwordField().bindText(item.getter, item.setter).component.toolTipText = item.toolTipText
    }

internal fun Panel.akskCollapsibleRow(akskSettings: CodeClient.AkSkSettings): CollapsibleRow =
    collapsibleGroup(akskSettings.groupTitle) {
        akskSettings.akItem?.let { ak ->
            akskPasswordRow(ak)
        }
        akskPasswordRow(akskSettings.skItem).applyIfNotNull(akskSettings.groupComment?.takeIf { it.isNotBlank() }) {
            rowComment(it)
        }
    }

class SenseCodeSettingsComponent(akskSettings: CodeClient.AkSkSettings?) : UserLoginPanel() {
    fun getSettingsPanel(
        userName: String?,
        alreadyLoggedIn: Boolean,
        isSupportLogin: Boolean,
        onLoginClick: ((ActionEvent, () -> Unit) -> Unit)? = null
    ): DialogPanel {
        setupUserLoginPanel(userName, alreadyLoggedIn, isSupportLogin, onLoginClick)
        return settingsPanel
    }

    override fun setAlreadyLoggedIn(alreadyLoggedIn: Boolean) {
        super.setAlreadyLoggedIn(alreadyLoggedIn)
        akskGroup?.expanded = !alreadyLoggedIn
    }

    private var akskGroup: CollapsibleRow? = null
    private val settingsPanel: DialogPanel = panel {
        row {
            cell(userLoginPanel)
        }

        akskGroup = akskSettings?.let {
            akskCollapsibleRow(it)
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
}
