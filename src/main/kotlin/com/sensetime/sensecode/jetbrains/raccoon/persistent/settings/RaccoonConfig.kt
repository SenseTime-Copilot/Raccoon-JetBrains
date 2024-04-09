package com.sensetime.sensecode.jetbrains.raccoon.persistent.settings

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonResources
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonBaseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


internal val RaccoonConfigJson = RaccoonBaseJson

@Serializable
internal data class RaccoonConfig(
    @SerialName("raccoonStatisticsMaxCacheCount")
    private val _raccoonStatisticsMaxCacheCount: Int = RACCOON_STATISTICS_MAX_CACHE_COUNT,
    @SerialName("raccoonStatisticsMaxIntervalMs")
    private val _raccoonStatisticsMaxIntervalMs: Int = RACCOON_STATISTICS_MAX_INTERVAL_MS
) {
    enum class Variant {
        TOC, TOB, TEAM
    }

    private val variant: Variant = Variant.TOC
    fun isToC(): Boolean = (variant == Variant.TOC)
    fun isToB(): Boolean = (variant == Variant.TOB)
    fun isTeam(): Boolean = (variant == Variant.TEAM)

    val raccoonStatisticsMaxCacheCount: Int
        get() = _raccoonStatisticsMaxCacheCount.coerceIn(1, RACCOON_STATISTICS_MAX_CACHE_COUNT)
    val raccoonStatisticsMaxIntervalMs: Int
        get() = _raccoonStatisticsMaxIntervalMs.coerceIn(1000, RACCOON_STATISTICS_MAX_INTERVAL_MS)


    companion object {
        private const val NAME = "config"
        private val LOG = logger<RaccoonConfig>()

        private const val RACCOON_STATISTICS_MAX_CACHE_COUNT = 100
        private const val RACCOON_STATISTICS_MAX_INTERVAL_MS = 600 * 1000

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
