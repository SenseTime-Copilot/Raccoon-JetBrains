package com.sensetime.sensecore.sensecodeplugin.configuration

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState.Companion.DEFAULT_PROMPT_GENERATION
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState.Companion.DEFAULT_SYSTEM_PROMPT_CHAT
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState.Companion.DEFAULT_PROMPT_TEST_GENERATION
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState.Companion.DEFAULT_PROMPT_CORRECTION
import com.sensetime.sensecore.sensecodeplugin.configuration.GptMentorSettingsState.Companion.DEFAULT_PROMPT_REFACTORING
import com.sensetime.sensecore.sensecodeplugin.domain.Model
import com.sensetime.sensecore.sensecodeplugin.security.GptMentorCredentialsManager
import com.sensetime.sensecore.sensecodeplugin.ui.common.ConstraintTextField
import com.sensetime.sensecore.sensecodeplugin.ui.common.NumberVerifier
import java.awt.*
import javax.swing.*


class GptMentorConfigurable : Configurable {
    private lateinit var settingsPanel: JPanel
    private val apiAccessKey = JBPasswordField()
    private val apiSecretKey = JBPasswordField()

    private val codeGenerationPrompt = createTextArea()
    private val testGenerationPrompt = createTextArea()
    private val codeCorrectionPrompt = createTextArea()
    private val codeRefactoringPrompt = createTextArea()
    private val chatSystemPrompt = createTextArea()
    private val modelComboBox = ComboBox(Model.values().map {
        it.code
    }.toTypedArray())
    private val temperature = ConstraintTextField(verifier = NumberVerifier(decimalField = true))
    private val maxTokens = ConstraintTextField()

    private val config: GptMentorSettingsState = GptMentorSettingsState.getInstance()

    private fun createTextArea() = JBTextArea().apply {
        lineWrap = true
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color.BLACK, Color.WHITE), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        )
    }


    override fun createComponent(): JComponent {
        settingsPanel = JPanel()
        return createFromConfig()
    }

    private fun createFromConfig(): JComponent {
        apiAccessKey.text = getAccessKey()
        apiSecretKey.text = getSecretKey()
        codeGenerationPrompt.text = config.promptCodeGeneration
        testGenerationPrompt.text = config.promptTestGeneration
        codeCorrectionPrompt.text = config.promptCodeCorrection
        codeRefactoringPrompt.text = config.promptCodeRefactoring
        chatSystemPrompt.text = config.systemPromptChat
        modelComboBox.selectedItem = config.selectedModel
        temperature.text = config.temperature.toString()
        maxTokens.text = config.maxTokens.toString()

        settingsPanel = JPanel()
        settingsPanel.layout = GridBagLayout()
        val c = GridBagConstraints()

        c.weightx = LABEL_WEIGHT
        c.weighty = ROW_WEIGHT
        c.fill = GridBagConstraints.BOTH
        c.anchor = GridBagConstraints.LINE_END
        c.insets = Insets(0, 0, GAP, GAP)

        val prompts = arrayListOf(
            codeGenerationPrompt,
            testGenerationPrompt,
            codeCorrectionPrompt,
            codeRefactoringPrompt,
            chatSystemPrompt
        )

        val labels = listOf<Component>(
            JLabel("Code Generation Prompt:", JLabel.TRAILING),
            JLabel("Test Generation Prompt:", JLabel.TRAILING),
            JLabel("Code Correction Prompt:", JLabel.TRAILING),
            JLabel("Code Refactoring Prompt:", JLabel.TRAILING),
            JLabel("Chat System Prompt:", JLabel.TRAILING)
        )

        var gridY = 0
        c.gridy = gridY
        c.gridx = 0
        settingsPanel.add(JLabel("AccessKey ID:", JLabel.TRAILING).apply { preferredSize = Dimension(100, 0) }, c)
        c.gridx = 1
        settingsPanel.add(apiAccessKey, c)

        c.gridy = ++gridY
        c.gridx = 0
        settingsPanel.add(JLabel("AccessKey Secret:", JLabel.TRAILING).apply { preferredSize = Dimension(100, 0) }, c)
        c.gridx = 1
        settingsPanel.add(apiSecretKey, c)

        c.gridy = ++gridY
        c.gridx = 0
        settingsPanel.add(JLabel("", JLabel.TRAILING), c)
        c.gridx = 1
        settingsPanel.add(
            JLabel(
                "SenseCore AccessKey ID and Secret, you can get it from \"https://console.sensecore.cn/iam/Security/access-key\"",
                JLabel.LEADING
            ), c
        )

        gridY = addAdvancedParameters(c, gridY)

        for (promptIndex in prompts.indices) {
            c.gridy = ++gridY
            c.gridx = 0
            settingsPanel.add(labels[promptIndex], c)

            c.gridx = 1
            c.weightx = COMPONENT_WEIGHT
            c.anchor = GridBagConstraints.LINE_START
            c.insets = Insets(0, GAP, GAP, 0)

            settingsPanel.add(prompts[promptIndex], c)

            val resetButton = JButton("Reset")
            resetButton.preferredSize = Dimension(RESET_BUTTON_WIDTH, RESET_BUTTON_HEIGHT)
            resetButton.addActionListener {
                resetPrompt(promptIndex)
            }
            c.gridy = ++gridY
            c.gridx = 1
            c.weightx = RESET_BUTTON_WEIGHT
            c.insets = Insets(0, GAP, GAP, 0)
            val buttonPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(resetButton)
                add(Box.createHorizontalGlue())
            }
            settingsPanel.add(buttonPanel, c)

            c.gridx = 0
            c.weightx = LABEL_WEIGHT
            c.insets = Insets(0, 0, GAP, GAP)
        }

        return settingsPanel
    }

    private fun addAdvancedParameters(c: GridBagConstraints, gridY: Int): Int {
        var gridYNew = addModelSelection(gridY, c)
        gridYNew = addTemperatureSelection(gridYNew, c)
        gridYNew = addMaxTokensSelection(gridYNew, c)
        return gridYNew
    }

    private fun addModelSelection(gridY: Int, c: GridBagConstraints): Int {
        var gridY1 = gridY
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("Model:", JLabel.TRAILING).apply { preferredSize = Dimension(100, 0) }, c)
        c.gridx = 1
        settingsPanel.add(modelComboBox, c)

        //        add label below combox with the text: gpt-4 is in limited beta. Check OpenAI for availability.
//        c.gridy = ++gridY1
//        c.gridx = 0
//        settingsPanel.add(JLabel("", JLabel.TRAILING), c)
//        c.gridx = 1
//        settingsPanel.add(JLabel("gpt-4 is in limited beta. See OpenAI for availability", JLabel.LEADING), c)
        return gridY1
    }

    private fun addTemperatureSelection(gridY: Int, c: GridBagConstraints): Int {
        var gridY1 = gridY
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("Temperature:", JLabel.TRAILING).apply { preferredSize = Dimension(100, 0) }, c)
        c.gridx = 1
        settingsPanel.add(temperature, c)
        // add label below textfield with the text: The sampling temperature to use. Lower values are more conservative, higher values are more creative.
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("", JLabel.TRAILING), c)
        c.gridx = 1
        settingsPanel.add(
            JLabel(
                "The temperature to use (0..2). Lower values are more conservative, higher values are more creative.",
                JLabel.LEADING
            ), c
        )
        return gridY1
    }

    private fun addMaxTokensSelection(gridY: Int, c: GridBagConstraints): Int {
        var gridY1 = gridY
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("Max Tokens:", JLabel.TRAILING).apply { preferredSize = Dimension(100, 0) }, c)
        c.gridx = 1
        settingsPanel.add(maxTokens, c)
        // add label below textfield with the text: The maximum number of tokens to generate in the output. Longer outputs may take longer to generate.
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("", JLabel.TRAILING), c)
        c.gridx = 1
        settingsPanel.add(
            JLabel(
                "The maximum number of tokens to generate in the output. Longer outputs may take longer to generate.",
                JLabel.LEADING
            ), c
        )
        c.gridy = ++gridY1
        c.gridx = 0
        settingsPanel.add(JLabel("", JLabel.TRAILING), c)
        c.gridx = 1
        settingsPanel.add(
            JLabel(
                "The maxtokens of the completion and the input combined cannot exceed 4096.",
                JLabel.LEADING
            ), c
        )
        return gridY1
    }

    private fun resetPrompt(i: Int) {
        when (i) {
            0 -> codeGenerationPrompt.text = DEFAULT_PROMPT_GENERATION
            1 -> testGenerationPrompt.text = DEFAULT_PROMPT_TEST_GENERATION
            2 -> codeCorrectionPrompt.text = DEFAULT_PROMPT_CORRECTION
            3 -> codeRefactoringPrompt.text = DEFAULT_PROMPT_REFACTORING
            4 -> chatSystemPrompt.text = DEFAULT_SYSTEM_PROMPT_CHAT
        }
    }

    override fun reset() {
        apiAccessKey.text = getAccessKey()
        apiSecretKey.text = getSecretKey()
        codeGenerationPrompt.text = config.promptCodeGeneration
        testGenerationPrompt.text = config.promptTestGeneration
        codeCorrectionPrompt.text = config.promptCodeCorrection
        codeRefactoringPrompt.text = config.promptCodeRefactoring
        chatSystemPrompt.text = config.systemPromptChat
        modelComboBox.selectedItem = config.selectedModel
        temperature.text = config.temperature.toString()
        maxTokens.text = config.maxTokens.toString()
        super.reset()
    }

    private fun getAccessKey() = GptMentorCredentialsManager.getAccessKey()
    private fun getSecretKey() = GptMentorCredentialsManager.getSecretKey()

    override fun isModified(): Boolean {
        var modified = false
        modified = modified || apiAccessKey.text != getAccessKey()
        modified = modified || apiSecretKey.text != getSecretKey()
        modified = modified || codeGenerationPrompt.text != config.promptCodeGeneration
        modified = modified || testGenerationPrompt.text != config.promptTestGeneration
        modified = modified || codeCorrectionPrompt.text != config.promptCodeCorrection
        modified = modified || codeRefactoringPrompt.text != config.promptCodeRefactoring
        modified = modified || chatSystemPrompt.text != config.systemPromptChat
        modified = modified || modelComboBox.selectedItem != config.selectedModel
        modified = modified || (temperature.text) != config.temperature.toString()
        modified = modified || maxTokens.text != config.maxTokens.toString()

        return modified
    }

    override fun apply() {
        if (isModified.not()) {
            return
        }

        config.promptCodeGeneration = codeGenerationPrompt.text
        config.promptTestGeneration = testGenerationPrompt.text
        config.promptCodeCorrection = codeCorrectionPrompt.text
        config.promptCodeRefactoring = codeRefactoringPrompt.text
        config.systemPromptChat = chatSystemPrompt.text
        config.selectedModel = modelComboBox.selectedItem as String
        config.temperature = (temperature.text.toFloatOrNull() ?: config.temperature).coerceIn(0f, 2f)
        config.maxTokens = (maxTokens.text.toIntOrNull() ?: config.maxTokens).coerceIn(1, 4096)

        GptMentorCredentialsManager.setAccessKey(apiAccessKey.text)
        GptMentorCredentialsManager.setSecretKey(apiSecretKey.text)
    }

    override fun getDisplayName(): String {
        return "GPT-Mentor"
    }

    companion object {
        private const val LABEL_WEIGHT = 0.3
        private const val COMPONENT_WEIGHT = 0.7
        private const val ROW_WEIGHT = 1.0
        private const val GAP = 10
        private const val RESET_BUTTON_WEIGHT = 0.2
        private const val RESET_BUTTON_WIDTH = 60
        private const val RESET_BUTTON_HEIGHT = 30
    }
}
