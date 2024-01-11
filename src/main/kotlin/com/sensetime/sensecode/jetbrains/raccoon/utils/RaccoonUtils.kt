package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.openapi.util.SystemInfo
import com.sensetime.sensecode.jetbrains.raccoon.persistent.RaccoonCredentialsManager
import java.util.UUID.randomUUID


// String

fun String.takeIfNotBlank(): String? = takeIf { it.isNotBlank() }

inline fun String?.ifNullOrBlankElse(defaultValue: String, onElse: (String) -> String): String =
    if (isNullOrBlank()) defaultValue else onElse(this)

fun String?.ifNullOrBlank(defaultValue: String = ""): String = ifNullOrBlankElse(defaultValue) { it }

inline fun <R> String.letIfNotBlank(block: (String) -> R): R? = takeIfNotBlank()?.let(block)


// List

fun <T> List<T>.takeIfNotEmpty(): List<T>? = takeIf { it.isNotEmpty() }
inline fun <T, R> List<T>.letIfNotEmpty(block: (List<T>) -> R): R? = takeIfNotEmpty()?.let(block)

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
    fun getCurrentTimestampS() = getCurrentTimestampMs() / 1000L

    const val DEFAULT_MACHINE_ID = "raccoon-jetbrains-machine-id-0000"
    fun generateUUID(): String = randomUUID().toString()
    val machineID: String? = kotlin.runCatching { getOrGenerateDeviceId() }.getOrNull()


    private fun getOSChar(): Char {
        if (SystemInfo.isWindows) return '1'
        else if (SystemInfo.isMac) return '2'
        else if (SystemInfo.isLinux) return '3'
        return '0'
    }

    private fun generateDeviceId(): String = "${getCurrentTimestampS()}-${getOSChar()}-${randomUUID()}"
    private fun getOrGenerateDeviceId(): String? {
        var result = RaccoonCredentialsManager.getDeviceID()
        if (result.isNullOrBlank()) {
            RaccoonCredentialsManager.setDeviceID(generateDeviceId())
            result = RaccoonCredentialsManager.getDeviceID()
        }
        return result
    }
}
