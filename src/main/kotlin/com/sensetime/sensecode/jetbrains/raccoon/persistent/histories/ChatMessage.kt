package com.sensetime.sensecode.jetbrains.raccoon.persistent.histories

import com.intellij.openapi.project.Project
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import com.sensetime.sensecode.jetbrains.raccoon.llm.prompts.PromptVariables
import com.sensetime.sensecode.jetbrains.raccoon.persistent.others.RaccoonUserInformation
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


internal interface DisplayMessage {
    val name: String
    val displayText: String
    val timestampMs: Long

    fun hasData(): Boolean
}

@Serializable
internal data class UserMessage(
    override val name: String,
    @SerialName("type")
    val promptType: String,
    override val timestampMs: Long,
    private val args: Map<String, String>,
) : DisplayMessage {
    fun getContent(modelConfig: ChatModelConfig): String =
        modelConfig.getPromptTemplate(promptType)!!.getRawText(args)

    override val displayText: String
        get() = RaccoonClient.clientConfig.chatModelConfig.getPromptTemplate(promptType)!!.getDisplayText(args)

    private val text: String?
        get() = args[PromptVariables.TEXT]
    private val code: String?
        get() = args[PromptVariables.CODE]

    override fun hasData(): Boolean = !(text.isNullOrBlank() && code.isNullOrBlank())

    companion object {
        fun createUserMessage(
            project: Project?,
            name: String? = RaccoonUserInformation.getInstance().getDisplayUserName(),
            promptType: String,
            text: String? = null,
            code: String? = null,
            language: String? = null,
            args: Map<String, String>? = null,
            timestampMs: Long = RaccoonUtils.getDateTimestampMs()
        ): UserMessage? = if (name.isNullOrBlank()) {
            RaccoonClient.notifyGotoLogin(project!!, null)
            null
        } else {
            UserMessage(name, promptType, timestampMs, buildMap {
                args?.let { putAll(it) }
                text?.let { put(PromptVariables.TEXT, it) }
                code?.let { put(PromptVariables.CODE, it) }
                language?.let { put(PromptVariables.LANGUAGE, it) }
            })
        }
    }
}

@Serializable
data class AssistantMessage(
    var content: String = "",
    @SerialName("state")
    var generateState: GenerateState = GenerateState.PROMPT,
    override val timestampMs: Long = RaccoonUtils.getDateTimestampMs()
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
        get() = RaccoonPlugin.name
    override val displayText: String
        get() = content

    override fun hasData(): Boolean = content.isNotBlank()

    companion object {
        @JvmStatic
        fun createPromptAssistantMessage(): AssistantMessage = AssistantMessage()
    }
}
