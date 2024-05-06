package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.openapi.diagnostic.logger
import java.lang.IllegalStateException


private val LOG = logger<RaccoonExceptions>()

internal fun Throwable.throwIfMustRethrow(): Exception =
    (this as? Exception)?.takeUnless { RaccoonExceptions.isMustRethrow(it) } ?: throw this

internal fun <R> Throwable.toResult(): Result<R> =
    Result.failure(throwIfMustRethrow().also { LOG.warnWithDebug("Caught $it, will not rethrow", it) })

internal class RaccoonFatalException(message: String) : Exception(message)


internal object RaccoonExceptions {
    private val mustRethrowExceptions =
        listOf(InterruptedException::class, IllegalStateException::class, RaccoonFatalException::class)

    @JvmStatic
    fun isMustRethrow(e: Exception): Boolean = mustRethrowExceptions.any { it.isInstance(e) }

    @JvmStatic
    inline fun <R> resultOf(runBlock: () -> R, onFinally: () -> Unit): Result<R> =
        try {
            Result.success(runBlock())
        } catch (e: Exception) {
            e.toResult()
        } finally {
            onFinally()
        }

    @JvmStatic
    inline fun <R> resultOf(block: () -> R): Result<R> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            e.toResult()
        }
}