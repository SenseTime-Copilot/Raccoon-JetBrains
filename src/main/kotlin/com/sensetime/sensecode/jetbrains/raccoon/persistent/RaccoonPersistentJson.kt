package com.sensetime.sensecode.jetbrains.raccoon.persistent

import kotlinx.serialization.json.Json

val RaccoonPersistentJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}