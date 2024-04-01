package com.sensetime.sensecode.jetbrains.raccoon.resources

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey


@NonNls
private const val BUNDLE = "messages.RaccoonBundle"

object RaccoonBundle {
    private val bundle = DynamicBundle(RaccoonBundle::class.java, BUNDLE)

    @Nls
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}