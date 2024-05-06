package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientJsonObject
import com.sensetime.sensecode.jetbrains.raccoon.utils.plusIfNotNull
import kotlinx.serialization.json.JsonElement


// real request body for each client, can be any JsonObject
// use JsonObject because of @Serializable not support class inheritance forward constructor args
// see "https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/basic-serialization.md#constructor-properties-requirement"
internal abstract class ClientRequest(
    requestArgs: Map<String, JsonElement>,
    customRequestArgs: Map<String, JsonElement>? = null
) : LLMClientJsonObject(requestArgs.plusIfNotNull(customRequestArgs))
