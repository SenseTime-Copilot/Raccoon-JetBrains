package com.sensetime.sensecore.sensecodeplugin.settings

import com.sensetime.sensecore.sensecodeplugin.clients.CodeClient

data class ClientConfig(
    val name: String,
    val constructor: () -> CodeClient,
    var apiEndpoint: String,
    val models: Map<String, ModelConfig>,
    val selectedModelNames: Map<String, String>,
    private val defaultModelName: String
) {
    fun getModelConfigByType(type: String): ModelConfig =
        models.getValue(selectedModelNames.getOrDefault(type, defaultModelName))

    companion object {
        const val FREE_CHAT = "FreeChat"
        const val INLINE_MIDDLE = "InlineMiddle"
        const val INLINE_END = "InlineEnd"
    }
}
