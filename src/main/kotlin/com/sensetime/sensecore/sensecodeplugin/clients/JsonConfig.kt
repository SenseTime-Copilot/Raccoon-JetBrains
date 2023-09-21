package com.sensetime.sensecore.sensecodeplugin.clients

import kotlinx.serialization.json.Json

val SenseCodeClientJson = Json {
    encodeDefaults = true
    coerceInputValues = true
    ignoreUnknownKeys = true
}