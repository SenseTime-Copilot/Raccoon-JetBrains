package com.sensetime.sensecode.jetbrains.raccoon.utils

import kotlinx.serialization.json.Json

internal val RaccoonBaseJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}