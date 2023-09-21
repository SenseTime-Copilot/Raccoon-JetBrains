package com.sensetime.sensecore.sensecodeplugin.settings

import com.intellij.util.xmlb.annotations.Transient
import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient

data class ClientConfig(
    val name: String,
    @Transient
    val constructor: () -> CodeClient,
    val apiEndpoint: String,
    val models: List<ModelConfig>,
    var actionsModelIndex: Int,
    var freeChatModelIndex: Int,
    var customModelIndex: Int,
    var inlineCompletionModelIndex: Int
)
