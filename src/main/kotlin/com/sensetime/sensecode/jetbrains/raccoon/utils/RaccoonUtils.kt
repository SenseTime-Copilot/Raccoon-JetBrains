package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.util.SystemInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID.randomUUID
import kotlin.coroutines.CoroutineContext


// String

internal fun String.takeIfNotBlank(): String? = takeIf { it.isNotBlank() }

internal inline fun String?.ifNullOrBlankElse(defaultValue: String, onElse: (String) -> String): String =
    if (isNullOrBlank()) defaultValue else onElse(this)

internal fun String?.ifNullOrBlank(defaultValue: String): String = ifNullOrBlankElse(defaultValue) { it }

internal inline fun <R> String.letIfNotBlank(block: (String) -> R): R? = takeIfNotBlank()?.let(block)

internal fun String.getNameFromEmail(): String = substringBefore('@')


// List

internal fun <T> List<T>.takeIfNotEmpty(): List<T>? = takeIf { it.isNotEmpty() }
internal inline fun <T, R> List<T>.letIfNotEmpty(block: (List<T>) -> R): R? = takeIfNotEmpty()?.let(block)

// Map

internal fun <K, V> Map<K, V>.plusIfNotNull(other: Map<K, V>?): Map<K, V> = (other?.let { this + it }) ?: this

internal fun <K, V> MutableMap<K, V>.getOrPutDefault(key: K, value: V): V = get(key) ?: value.also { put(key, it) }

internal fun <K, V> MutableMap<K, V>.putUnique(key: K, value: V) {
    require(!containsKey(key)) { "Found same key $key, prev value is ${get(key)}, current is $value" }
    put(key, value)
}

internal inline fun <K, V> MutableMap<K, V>.putIf(key: K, value: V, predicate: (MutableMap<K, V>) -> Boolean) {
    if (predicate(this)) {
        put(key, value)
    }
}


// Json

internal fun Map<String, JsonElement>.or(other: Map<String, JsonElement>?): JsonObject =
    JsonObject(other?.let { this + it } ?: this)

internal fun List<Map<String, JsonElement>>.join(): JsonObject =
    JsonObject(reduce { accumulator, current -> accumulator.or(current) })


// Coroutines

internal fun CoroutineContext.plusIfNotNull(other: CoroutineContext?): CoroutineContext =
    (other?.let { this + it }) ?: this


internal object RaccoonUtils {
    // Date

    fun getDateTimestampMs(): Long = System.currentTimeMillis()
    fun getDateTimestampS(): Long = getDateTimestampMs() / 1000L

    fun getSteadyTimestampMs(): Long = System.nanoTime() / (1000L * 1000L)

    fun getFormattedUTCDate(): String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    fun Long.isExpiredS(advanceS: Long = 0L): Boolean = (getDateTimestampS() + advanceS) > this


    // uuid

    private val OS_TAG: Char =
        if (SystemInfo.isWindows) '1' else if (SystemInfo.isMac) '2' else if (SystemInfo.isLinux) '3' else '0'

    private fun makeRaccoonUUID(uuid: String): String = "$OS_TAG-$uuid"

    val machineID: String = makeRaccoonUUID(PermanentInstallationID.get())
    fun generateUUID(): String = makeRaccoonUUID(randomUUID().toString())
}
