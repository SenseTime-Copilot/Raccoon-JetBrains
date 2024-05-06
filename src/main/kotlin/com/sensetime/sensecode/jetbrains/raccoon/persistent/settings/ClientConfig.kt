package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import kotlinx.serialization.Serializable


internal interface ClientConfig {
    @Serializable
    abstract class ClientApiConfig<out T : ModelConfig> {
        abstract val path: String
        protected abstract val models: List<T>
        private val selectedModelIndex: Int = 0
        val selectedModelConfig: T
            get() = models[selectedModelIndex]
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

    fun getApiEndpoint(apiPath: String): String = apiBaseUrl + apiPath
    fun getCompletionApiEndpoint(): String = getApiEndpoint(completionApiConfig.path)
    fun getChatApiEndpoint(): String = getApiEndpoint(chatApiConfig.path)
    fun getAgentApiEndpoint(): String = getApiEndpoint(agentApiConfig.path)
}
