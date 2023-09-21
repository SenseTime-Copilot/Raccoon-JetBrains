package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.util.xmlb.annotations.Transient
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient

data class ClientConfig(
    @Transient
    val name: String,
    @Transient
    val constructor: () -> CodeClient,
    @Transient
    val actionsModelName: String,
    @Transient
    val freeChatModelName: String,
    @Transient
    val customModelName: String,
    @Transient
    val inlineCompletionModelName: String,
    val apiEndpoint: String,
    @Transient
    val models: Map<String, ModelConfig>
)
