package com.sensetime.sensecode.jetbrains.raccoon.clients

import kotlinx.serialization.json.Json

val RaccoonClientJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}