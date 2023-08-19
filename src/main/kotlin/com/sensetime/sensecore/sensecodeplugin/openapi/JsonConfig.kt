package com.sensetime.sensecore.sensecodeplugin.openapi

import kotlinx.serialization.json.Json

val JSON = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}
