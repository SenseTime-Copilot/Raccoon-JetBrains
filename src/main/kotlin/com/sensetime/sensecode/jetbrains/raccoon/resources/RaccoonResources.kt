package com.sensetime.sensecode.jetbrains.raccoon.resources

object RaccoonResources {
    @JvmStatic
    fun getResourceContent(resourcePath: String): String? =
        javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes().decodeToString() }
}