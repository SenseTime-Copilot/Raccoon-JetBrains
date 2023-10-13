//package com.sensetime.sensecore.sensecodeplugin.toolwindows
//
//import com.intellij.openapi.application.ApplicationManager
//import com.intellij.openapi.components.PersistentStateComponent
//import com.intellij.openapi.components.State
//import com.intellij.openapi.components.Storage
//import com.intellij.util.xmlb.XmlSerializerUtil
//import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodePlugin
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.Json
//
//internal val SenseCodeChatHistoryJson = Json {
//    encodeDefaults = true
//    coerceInputValues = true
//    ignoreUnknownKeys = true
//}
//
//internal fun SenseCodeChatHistoryState.Conversation.encodeToJsonString() =
//    SenseCodeChatHistoryJson.encodeToString(SenseCodeChatHistoryState.Conversation.serializer(), this)
//
//internal fun SenseCodeChatHistoryState.History.encodeToJsonString() =
//    SenseCodeChatHistoryJson.encodeToString(SenseCodeChatHistoryState.History.serializer(), this)
//
//internal fun List<String>.toHistories(): List<SenseCodeChatHistoryState.History> =
//    map { SenseCodeChatHistoryJson.decodeFromString(SenseCodeChatHistoryState.History.serializer(), it) }
//
//internal fun List<String>.toConversations(): List<SenseCodeChatHistoryState.Conversation> =
//    map { SenseCodeChatHistoryJson.decodeFromString(SenseCodeChatHistoryState.Conversation.serializer(), it) }
//
