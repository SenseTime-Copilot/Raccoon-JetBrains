package com.sensetime.sensecore.sensecodeplugin.services.http.authentication

import com.intellij.util.Url

interface CodeAuthServiceBase {
    val baseUrl: Url

    companion object {
        val SENSECODE_PREFIX = "sensecode/auth"
    }
}