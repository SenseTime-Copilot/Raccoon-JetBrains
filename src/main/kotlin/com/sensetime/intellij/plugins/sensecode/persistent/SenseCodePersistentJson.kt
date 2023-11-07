package com.sensetime.intellij.plugins.sensecode.persistent

import kotlinx.serialization.json.Json

val SenseCodePersistentJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}