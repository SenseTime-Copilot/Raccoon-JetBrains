package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.internal.statistic.DeviceIdManager
import java.util.UUID.randomUUID


// String

fun String.takeIfNotBlank(): String? = takeIf { it.isNotBlank() }

inline fun String?.ifNullOrBlankElse(defaultValue: String, onElse: (String) -> String): String =
    if (isNullOrBlank()) defaultValue else onElse(this)

fun String?.ifNullOrBlank(defaultValue: String = ""): String = ifNullOrBlankElse(defaultValue) { it }

inline fun <R> String.letIfNotBlank(block: (String) -> R): R? = takeIfNotBlank()?.let(block)


// List

fun <T> List<T>.takeIfNotEmpty(): List<T>? = takeIf { it.isNotEmpty() }


// Map

fun <K, V> MutableMap<K, V>.putUnique(key: K, value: V) {
    require(!containsKey(key)) { "Found same key $key, prev value is ${get(key)}, current is $value" }
    put(key, value)
}

inline fun <K, V> MutableMap<K, V>.putIf(key: K, value: V, predicate: (MutableMap<K, V>) -> Boolean) {
    if (predicate(this)) {
        put(key, value)
    }
}

object RaccoonUtils {
    fun getCurrentTimestampMs() = System.currentTimeMillis()

    const val DEFAULT_MACHINE_ID = "raccoon-jetbrains-machine-id-0000"
    fun generateUUID(): String = randomUUID().toString()
    val machineID: String? by lazy {
        kotlin.runCatching {
            DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "RaccoonJetBrains").takeIfNotBlank()
        }.getOrNull()
    }
}
