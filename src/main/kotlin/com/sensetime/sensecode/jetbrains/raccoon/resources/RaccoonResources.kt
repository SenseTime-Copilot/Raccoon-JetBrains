package com.sensetime.sensecode.jetbrains.raccoon.resources

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

object RaccoonResources {
    const val CONFIGS_DIR = "/configs"
    private val LOG = logger<RaccoonResources>()

    @JvmStatic
    fun getResourceContent(resourcePath: String): String? =
        javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes().decodeToString() }
            .also { LOG.debug { "Get resource from \"$resourcePath\", result length(${it?.length})" } }
}