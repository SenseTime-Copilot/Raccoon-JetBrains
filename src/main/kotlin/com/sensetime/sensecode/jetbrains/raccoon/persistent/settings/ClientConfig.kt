package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

interface ClientConfig {
    abstract class ClientApiConfig<T : ModelConfig> {
        protected abstract val path: String
        protected abstract val selectedModelName: String
        protected abstract val models: Map<String, T>

        val selectedModelConfig: T
            get() = models.getValue(selectedModelName)

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

fun List<ClientConfig>.toClientConfigMap(): Map<String, ClientConfig> = associateBy(ClientConfig::name)
