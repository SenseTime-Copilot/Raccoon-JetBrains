package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import kotlinx.serialization.Serializable

internal interface ClientConfig {
    @Serializable
    abstract class ClientApiConfig<T : ModelConfig> {
        protected abstract val path: String
        protected abstract val models: List<T>
        private val selectedModelIndex: Int = 0
        val selectedModelConfig: T
            get() = models[selectedModelIndex]

        fun getApiEndpoint(apiBaseUrl: String): String = apiBaseUrl + path
    }

    val name: String
    val apiBaseUrl: String
    val completionApiConfig: ClientApiConfig<CompletionModelConfig>
    val chatApiConfig: ClientApiConfig<ChatModelConfig>
    val agentApiConfig: ClientApiConfig<AgentModelConfig>

    val completionModelConfig: CompletionModelConfig
        get() = completionApiConfig.selectedModelConfig
    val chatModelConfig: ChatModelConfig
        get() = chatApiConfig.selectedModelConfig
    val agentModelConfig: AgentModelConfig
        get() = agentApiConfig.selectedModelConfig

    fun getCompletionApiEndpoint(): String = completionApiConfig.getApiEndpoint(apiBaseUrl)
    fun getChatApiEndpoint(): String = chatApiConfig.getApiEndpoint(apiBaseUrl)
    fun getAgentApiEndpoint(): String = agentApiConfig.getApiEndpoint(apiBaseUrl)
}
