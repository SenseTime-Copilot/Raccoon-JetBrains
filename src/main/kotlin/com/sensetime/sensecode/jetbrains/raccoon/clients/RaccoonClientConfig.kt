package com.sensetime.sensecode.jetbrains.raccoon.clients

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.sensetime.sensecode.jetbrains.raccoon.llm.models.PenroseChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.llm.models.PenroseCompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ChatModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.ClientConfig
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.CompletionModelConfig
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonResources
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


private val LOG = logger<RaccoonClientConfig>()

@Serializable
data class RaccoonCompletionApiConfig(
    override val path: String = "/api/plugin/nova/v1/proxy/v1/llm/completions",
    override val selectedModelIndex: Int = 0,
    override val models: List<CompletionModelConfig> = listOf(PenroseCompletionModelConfig())
) : ClientConfig.ClientApiConfig<CompletionModelConfig>()

@Serializable
data class RaccoonChatApiConfig(
    override val path: String = "/api/plugin/nova/v1/proxy/v1/llm/chat-completions",
    override val selectedModelIndex: Int = 0,
    override val models: List<ChatModelConfig> = listOf(PenroseChatModelConfig())
) : ClientConfig.ClientApiConfig<ChatModelConfig>()

@Serializable
data class RaccoonClientConfig(
    override val apiBaseUrl: String = "https://raccoon-api.sensetime.com"
) : ClientConfig {
    @Transient
    override val name: String = NAME
    override val completionApiConfig: ClientConfig.ClientApiConfig<CompletionModelConfig> = RaccoonCompletionApiConfig()
    override val chatApiConfig: ClientConfig.ClientApiConfig<ChatModelConfig> = RaccoonChatApiConfig()

    companion object {
        private const val NAME = "RaccoonClient"

        @JvmStatic
        fun loadFromJsonString(jsonString: String): RaccoonClientConfig = RaccoonClientJson.decodeFromString(
            serializer(), jsonString.also { LOG.debug { "Load RaccoonClientConfig from json $it" } })
            .also { LOG.trace { "Load ClientConfig ok, result $it" } }

        @JvmStatic
        fun loadFromResources(): RaccoonClientConfig =
            loadFromJsonString(requireNotNull(RaccoonResources.getResourceContent("${RaccoonResources.CONFIGS_DIR}/$NAME.json"))).also {
                require(NAME == it.name) { "Client name: expected ($NAME) != actual (${it.name})" }
            }
    }
}