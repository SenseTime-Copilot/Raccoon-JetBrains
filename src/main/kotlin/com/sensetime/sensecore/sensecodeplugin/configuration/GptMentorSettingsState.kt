package com.sensetime.sensecore.sensecodeplugin.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.domain.Model

/**
 * Supports storing the application settings in a persistent way.
 * The {@link State} and {@link Storage} annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(
    name = "com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState",
    storages = [Storage("GptMentorConfig.xml")]
)
class GptMentorSettingsState : PersistentStateComponent<GptMentorSettingsState> {
    var promptCodeGeneration: String = DEFAULT_PROMPT_GENERATION
    var promptTestGeneration: String = DEFAULT_PROMPT_TEST_GENERATION
    var promptCodeCorrection: String = DEFAULT_PROMPT_CORRECTION
    var promptCodeRefactoring: String = DEFAULT_PROMPT_REFACTORING
    var systemPromptChat: String = DEFAULT_SYSTEM_PROMPT_CHAT
    var selectedModel: String = Model.PENROSE_411.code
    var temperature: Float = DEFAULT_TEMPERATURE
    var maxTokens: Int = DEFAULT_MAX_TOKENS

    val model: Model
        get() {
            return Model.fromCode(selectedModel)
        }

    override fun getState(): GptMentorSettingsState {
        return this
    }

    override fun loadState(state: GptMentorSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): GptMentorSettingsState =
            ApplicationManager.getApplication().getService(GptMentorSettingsState::class.java)

        val DEFAULT_PROMPT_GENERATION = """
            ### Instruction:
            Task type: code generation. Please provide an explanation at the end.
            
            ### Input:
    """.trimIndent()

        val DEFAULT_PROMPT_TEST_GENERATION = """
            ### Instruction:
            Task type: test sample generation. Please provide an explanation at the end.
            
            ### Input:
    """.trimIndent()

        val DEFAULT_PROMPT_CORRECTION = """
            ### Instruction:
            Task type: code error correction. Please provide an explanation at the end.
            
            ### Input:
    """.trimIndent()

        val DEFAULT_PROMPT_REFACTORING = """
            ### Instruction:
            Task type: code refactoring and optimization. Please provide an explanation at the end.
            
            ### Input:
    """.trimIndent()

        const val DEFAULT_SYSTEM_PROMPT_CHAT = ""

        const val DEFAULT_TEMPERATURE = 0.5f
        const val DEFAULT_MAX_TOKENS = 256
    }
}
