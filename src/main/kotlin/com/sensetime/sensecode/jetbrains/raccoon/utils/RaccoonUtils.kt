package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.intellij.psi.PsiElement


// String

fun String.takeIfNotBlank(): String? = takeIf { it.isNotBlank() }

inline fun String?.ifNullOrBlankElse(defaultValue: String, onElse: (String) -> String): String =
    if (isNullOrBlank()) defaultValue else onElse(this)

fun String?.ifNullOrBlank(defaultValue: String = ""): String = ifNullOrBlankElse(defaultValue) { it }

inline fun <R> String.letIfNotBlank(block: (String) -> R): R? = takeIfNotBlank()?.let(block)


// List

fun <T> List<T>.takeIfNotEmpty(): List<T>? = takeIf { it.isNotEmpty() }

object RaccoonUtils {
    fun getCurrentTimestampMs() = System.currentTimeMillis()

    fun getMarkdownLanguage(psiElement: PsiElement?): String = psiElement?.language?.id?.lowercase() ?: ""
}
