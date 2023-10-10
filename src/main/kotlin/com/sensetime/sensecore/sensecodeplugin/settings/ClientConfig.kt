package com.sensetime.sensecore.sensecodeplugin.settings

import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient

data class ClientConfig(
    val name: String,
    val constructor: () -> CodeClient,
    val actionsModelName: String,
    val freeChatModelName: String,
    val customModelName: String,
    val inlineCompletionModelName: String,
    var apiEndpoint: String,
    val models: Map<String, ModelConfig>
)
