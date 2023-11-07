package com.sensetime.intellij.plugins.sensecode.clients

import kotlinx.serialization.json.Json

val SenseCodeClientJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}