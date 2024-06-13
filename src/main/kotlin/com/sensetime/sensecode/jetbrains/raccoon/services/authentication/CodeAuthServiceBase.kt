package com.sensetime.sensecode.jetbrains.raccoon.services.authentication

import com.intellij.util.Url

interface CodeAuthServiceBase {
    val baseUrl: Url

    companion object {
        val SENSECODE_PREFIX = "sensecode/auth"
    }
}
