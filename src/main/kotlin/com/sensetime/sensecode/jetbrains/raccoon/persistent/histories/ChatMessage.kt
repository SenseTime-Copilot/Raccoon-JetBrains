package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClientManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface DisplayMessage {
    val name: String
    val displayText: String
    val timestampMs: Long

    fun hasData(): Boolean
}

@Serializable
data class UserMessage(
    override val name: String,
    @SerialName("type")
    val promptType: String,
    override val timestampMs: Long,
    private val args: Map<String, String>,
) : DisplayMessage {
    fun getContent(modelConfig: ModelConfig): String =
        modelConfig.getPromptTemplate(promptType)!!.toRawText(args)

    override val displayText: String
        get() = RaccoonSettingsState.selectedClientConfig.toolwindowModelConfig.getPromptTemplate(promptType)!!
            .toDisplayText(args)

    private val text: String?
        get() = args[ModelConfig.DisplayTextTemplate.TEXT]
    private val code: String?
        get() = args[ModelConfig.DisplayTextTemplate.CODE]

    override fun hasData(): Boolean = !(text.isNullOrBlank() && code.isNullOrBlank())

    companion object {
        fun createUserMessage(
            name: String? = RaccoonClientManager.userName,
            promptType: String,
            text: String? = null,
            code: String? = null,
            language: String? = null,
            args: Map<String, String>? = null,
            timestampMs: Long = RaccoonUtils.getSystemTimestampMs()
        ): UserMessage? = if (name.isNullOrBlank()) {
            RaccoonNotification.notifyGotoLogin()
            null
        } else {
            UserMessage(name, promptType, timestampMs, buildMap {
                args?.let { putAll(it) }
                text?.let { put(ModelConfig.DisplayTextTemplate.TEXT, it) }
                code?.let { put(ModelConfig.DisplayTextTemplate.CODE, it) }
                language?.let { put(ModelConfig.DisplayTextTemplate.LANGUAGE, it) }
            })
        }
    }
}

@Serializable
data class AssistantMessage(
    var content: String = "",
    @SerialName("state")
    var generateState: GenerateState = GenerateState.PROMPT,
    override val timestampMs: Long = RaccoonUtils.getSystemTimestampMs()
) : DisplayMessage {
    enum class GenerateState(val code: String) {
        PROMPT("prompt"),
        DONE("done"),
        STOPPED("stopped"),
        WARNING("warning"),
        ERROR("error")
    }

    private fun isContentBlank(): Boolean =
        null == content.firstOrNull { !(it.isWhitespace() || (CharCategory.FORMAT == it.category)) }

    fun updateGenerateState(state: GenerateState): Pair<String, GenerateState> {
        return if ((GenerateState.DONE == state) && (isContentBlank())) {
            generateState = GenerateState.WARNING
            content = RaccoonBundle.message("toolwindow.content.conversation.assistant.empty")
            Pair(content, generateState)
        } else {
            generateState = state
            Pair("", generateState)
        }
    }

    override val name: String
        get() = RaccoonPlugin.NAME
    override val displayText: String
        get() = content

    override fun hasData(): Boolean = content.isNotBlank()

    companion object {
        @JvmStatic
        fun createPromptAssistantMessage(): AssistantMessage = AssistantMessage()
    }
}
