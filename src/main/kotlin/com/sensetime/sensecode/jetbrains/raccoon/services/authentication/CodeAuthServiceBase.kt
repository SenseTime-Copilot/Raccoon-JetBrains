package com.sensetime.intellij.plugins.sensecode.services.authentication

import com.intellij.util.Url

interface CodeAuthServiceBase {
    val baseUrl: Url

    companion object {
        val SENSECODE_PREFIX = "sensecode/auth"
    }
}
