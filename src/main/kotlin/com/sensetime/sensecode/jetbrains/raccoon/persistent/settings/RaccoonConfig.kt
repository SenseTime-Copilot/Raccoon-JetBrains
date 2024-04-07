package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonResources
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonBaseJson
import kotlinx.serialization.Serializable


internal val RaccoonConfigJson = RaccoonBaseJson

@Serializable
data class RaccoonConfig(
    val variant: Variant = Variant.TOC
) {
    enum class Variant {
        TOC, TOB, TEAM
    }

    companion object {
        private const val NAME = "config"
        private val LOG = logger<RaccoonConfig>()

        @JvmField
        val config: RaccoonConfig = loadFromResources()

        @JvmStatic
        private fun loadFromJsonString(jsonString: String): RaccoonConfig = RaccoonConfigJson.decodeFromString(
            serializer(), jsonString.also { LOG.debug { "Load $NAME from json $it" } })
            .also { LOG.trace { "Load $NAME ok, result ${RaccoonConfigJson.encodeToString(serializer(), it)}" } }

        @JvmStatic
        private fun loadFromResources(): RaccoonConfig =
            loadFromJsonString(requireNotNull(RaccoonResources.getResourceContent("${RaccoonResources.CONFIGS_DIR}/$NAME.json")))
    }
}
