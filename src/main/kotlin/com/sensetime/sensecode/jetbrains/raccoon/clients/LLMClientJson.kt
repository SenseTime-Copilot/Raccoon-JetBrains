package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonBaseJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


internal val LLMClientJson = RaccoonBaseJson

open class LLMClientJsonObject(content: Map<String, JsonElement>) : Map<String, JsonElement> by content {
    val jsonObject: JsonObject = JsonObject(content)
    fun toJsonString(): String = LLMClientJson.encodeToString(JsonObject.serializer(), jsonObject)
}

fun List<LLMClientJsonObject>.toJsonArray(): JsonArray = JsonArray(map { it.jsonObject })
