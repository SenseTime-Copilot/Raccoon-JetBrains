package com.sensetime.sensecore.sensecodeplugin.toolwindows.common

import kotlinx.serialization.json.Json

val SenseCodeChatJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}