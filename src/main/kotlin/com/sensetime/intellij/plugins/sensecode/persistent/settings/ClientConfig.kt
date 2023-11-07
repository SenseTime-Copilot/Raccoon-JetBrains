package com.sensetime.intellij.plugins.sensecode.persistent.settings

import kotlinx.serialization.Serializable

@Serializable
data class ClientConfig(
    val name: String,
    val inlineApiPath: String,
    val toolwindowApiPath: String,
    private val apis: Map<String, ClientApiConfig>
) {
    @Serializable
    data class ClientApiConfig(
        val path: String,
        val selectedModelName: String,
        val models: Map<String, ModelConfig>
    ) {
        val selectedModelConfig: ModelConfig
            get() = models.getValue(selectedModelName)
    }

    val inlineClientApiConfig: ClientApiConfig
        get() = apis.getValue(inlineApiPath)
    val toolwindowClientApiConfig: ClientApiConfig
        get() = apis.getValue(toolwindowApiPath)

    val inlineModelConfig: ModelConfig
        get() = inlineClientApiConfig.selectedModelConfig
    val toolwindowModelConfig: ModelConfig
        get() = toolwindowClientApiConfig.selectedModelConfig
}

fun List<ClientConfig>.toClientConfigMap(): Map<String, ClientConfig> = associateBy(ClientConfig::name)
fun List<ClientConfig.ClientApiConfig>.toClientApiConfigMap(): Map<String, ClientConfig.ClientApiConfig> =
    associateBy(ClientConfig.ClientApiConfig::path)
