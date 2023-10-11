package com.sensetime.sensecore.sensecodeplugin.clients

import kotlinx.serialization.json.Json

internal val SenseCodeClientJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}