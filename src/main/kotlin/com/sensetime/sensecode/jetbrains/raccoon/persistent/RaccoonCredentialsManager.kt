package com.sensetime.sensecode.jetbrains.raccoon.persistent

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonConfig
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_CREDENTIALS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank


internal fun Credentials.takeIfNotEmpty(): Credentials? =
    takeUnless { it.userName.isNullOrBlank() && it.getPasswordAsString().isNullOrBlank() }

internal inline fun <R> Credentials.letIfFilled(block: (String, String) -> R): R? =
    userName?.letIfNotBlank { user -> getPasswordAsString()?.letIfNotBlank { password -> block(user, password) } }

internal object RaccoonCredentialsManager {
    // PasswordSafe wrapper

    fun generateKeyWithIde(key: String): String =
        generateServiceName("${ApplicationInfo.getInstance().build.asString()} ${RaccoonPlugin.name}", key)

    private fun createCredentialAttributes(key: String): CredentialAttributes =
        CredentialAttributes(generateKeyWithIde(key))

    private fun getPasswordSafe(attributes: CredentialAttributes): Credentials? = PasswordSafe.instance.get(attributes)
    private fun setPasswordSafe(attributes: CredentialAttributes, credentials: Credentials?) {
        PasswordSafe.instance.set(attributes, credentials?.takeIfNotEmpty())
    }


    // client authorization

    private fun createClientAuthCredentialAttributes(name: String, key: String): CredentialAttributes =
        createCredentialAttributes("client.auth.$name.$key")

    private fun getClientAuth(name: String, key: String): Credentials? =
        getPasswordSafe(createClientAuthCredentialAttributes(name, key))

    private fun setClientAuth(name: String, key: String, credentials: Credentials? = null) {
        setPasswordSafe(createClientAuthCredentialAttributes(name, key), credentials)
        ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_CREDENTIALS_TOPIC)
            .onClientAuthChanged(name, key)
    }

    private const val ACCESS_TOKEN_KEY = "accessToken"
    fun getAccessToken(name: String): Credentials? = getClientAuth(name, ACCESS_TOKEN_KEY)
    fun setAccessToken(name: String, credentials: Credentials? = null) {
        setClientAuth(name, ACCESS_TOKEN_KEY, credentials)
    }

    private const val REFRESH_TOKEN_KEY = "refreshToken"
    fun getRefreshToken(name: String): Credentials? = getClientAuth(name, REFRESH_TOKEN_KEY)
    fun setRefreshToken(name: String, credentials: Credentials? = null) {
        setClientAuth(name, REFRESH_TOKEN_KEY, credentials)
    }

    private const val LOGIN_INFO_KEY = "loginInfo"
    private fun String.checkToBName(): String = if (RaccoonConfig.config.isToB()) this + "ToB" else this
    fun getLoginInfo(name: String): Credentials? = getClientAuth(name.checkToBName(), LOGIN_INFO_KEY)
    fun setLoginInfo(name: String, credentials: Credentials? = null) {
        setClientAuth(name.checkToBName(), LOGIN_INFO_KEY, credentials)
    }
}