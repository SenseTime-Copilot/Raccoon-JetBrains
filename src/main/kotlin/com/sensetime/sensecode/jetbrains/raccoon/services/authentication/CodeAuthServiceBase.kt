package com.sensetime.sensecode.jetbrains.raccoon.services.authentication

import com.intellij.util.Url

interface CodeAuthServiceBase {
    val baseUrl: Url

    companion object {
        val RACCOON_PREFIX = "raccoon/auth"
    }
}