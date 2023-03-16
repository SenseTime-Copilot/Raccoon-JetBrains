package com.github.jcraane.gptmentorplugin.ui.history.state

import com.github.jcraane.gptmentorplugin.openapi.JSON
import com.github.jcraane.gptmentorplugin.openapi.request.ChatGptRequest
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.reflect.KProperty

@State(
    name = "HistoryState",
    storages = [Storage("HistoryState.xml")]
)
class HistoryState : PersistentStateComponent<HistoryState> {
    var jsonBlob: String = "{}"

    var history: History by this

    override fun getState(): HistoryState? {
        return this
    }

    override fun loadState(state: HistoryState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): History {
        return if (jsonBlob.isNotEmpty()) {
            JSON.decodeFromString(History.serializer(), jsonBlob)
        } else {
            History(emptyList())
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: History) {
        jsonBlob = JSON.encodeToString(History.serializer(), value)
    }

    companion object {
        fun getInstance() = ServiceManager.getService(HistoryState::class.java)
    }
}

@Serializable
data class History(
    val items: List<HistoryItem>,
)

@Serializable
data class HistoryItem(
    val id: String,
    val title: String,
    val messages: List<HistoryMessage> = emptyList(),
) {
    companion object {
        const val NO_TITLE_PLACEHOLDER = "No title"

        fun from(chatGptRequest: ChatGptRequest) = HistoryItem(
            id = UUID.randomUUID().toString(),
            title = chatGptRequest.title,
            messages = chatGptRequest.messages.map {
                HistoryMessage(
                    content = it.content,
                    role = it.role.code,
                )
            }
        )

        private val ChatGptRequest.title: String
            get() {
                return messages.firstOrNull()?.content?.split(" ")?.take(20)?.joinToString(" ") ?: NO_TITLE_PLACEHOLDER
            }
    }
}

@Serializable
data class HistoryMessage(
    val role: String,
    val content: String,
)
