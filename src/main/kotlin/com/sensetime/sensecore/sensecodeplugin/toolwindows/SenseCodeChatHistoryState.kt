package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val SenseCodeChatHistoryJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}

internal fun SenseCodeChatHistoryState.Conversation.encodeToJsonString() =
    SenseCodeChatHistoryJson.encodeToString(SenseCodeChatHistoryState.Conversation.serializer(), this)

internal fun SenseCodeChatHistoryState.History.encodeToJsonString() =
    SenseCodeChatHistoryJson.encodeToString(SenseCodeChatHistoryState.History.serializer(), this)

internal fun List<String>.toHistories(): List<SenseCodeChatHistoryState.History> =
    map { SenseCodeChatHistoryJson.decodeFromString(SenseCodeChatHistoryState.History.serializer(), it) }

internal fun List<String>.toConversations(): List<SenseCodeChatHistoryState.Conversation> =
    map { SenseCodeChatHistoryJson.decodeFromString(SenseCodeChatHistoryState.Conversation.serializer(), it) }

@State(
    name = "com.sensetime.sensecore.sensecodeplugin.toolwindows.SenseCodeChatHistoryState",
    storages = [Storage("SenseCodeIntelliJChatHistory.xml")]
)
data class SenseCodeChatHistoryState(
    var version: String = ""
) : PersistentStateComponent<SenseCodeChatHistoryState> {
    enum class ChatType(val code: String) {
        FREE_CHAT("freeChat"),
        CODE_TASK("codeTask")
    }

    enum class GenerateState(val code: String) {
        ONLY_PROMPT("onlyPrompt"),
        DONE("done"),
        STOPPED("stopped"),
        ERROR("error")
    }

    @Serializable
    data class Message(
        val displayName: String,
        var displayText: String = "",
        val prompt: String? = null,
        val datetime: String = Utils.getCurrentDatetimeString()
    ) {
        fun getPromptString(): String = prompt ?: displayText
    }

    @Serializable
    data class Conversation(
        val user: Message,
        val assistant: Message = Message(SenseCodePlugin.NAME, ""),
        var generateState: GenerateState = GenerateState.ONLY_PROMPT
    )

    @Serializable
    data class History(
        val chatType: ChatType,
        val conversations: List<Conversation>,
        val saveDatetime: String = Utils.getCurrentDatetimeString()
    )

    var histories: MutableList<String> = mutableListOf()
    var lastFreeChatConversations: MutableList<String> = mutableListOf()

    override fun getState(): SenseCodeChatHistoryState {
        if (version != SenseCodePlugin.version) {
            version = SenseCodePlugin.version
        }
        return this
    }

    fun restore() {
        loadState(SenseCodeChatHistoryState(SenseCodePlugin.version))
    }

    override fun loadState(state: SenseCodeChatHistoryState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SenseCodeChatHistoryState
            get() = ApplicationManager.getApplication().getService(SenseCodeChatHistoryState::class.java)
    }
}