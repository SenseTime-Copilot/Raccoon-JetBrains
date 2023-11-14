package com.sensetime.sensecode.jetbrains.raccoon.persistent

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_CREDENTIALS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonPlugin
import com.sensetime.sensecode.jetbrains.raccoon.utils.letIfNotBlank

fun <R> Credentials.letIfFilled(block: (String, String) -> R): R? =
    userName?.letIfNotBlank { user -> getPasswordAsString()?.letIfNotBlank { password -> block(user, password) } }

object RaccoonCredentialsManager {
    fun getClientAuth(name: String, key: String): Credentials? =
        getPasswordSafe(createClientAuthCredentialAttributes(name, key))

    fun setClientAuth(name: String, key: String, credentials: Credentials? = null) {
        setPasswordSafe(createClientAuthCredentialAttributes(name, key), credentials)
        ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_CREDENTIALS_TOPIC)
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

    private const val AKSK_KEY = "aksk"
    fun getClientAkSk(name: String): Credentials? = getClientAuth(name, AKSK_KEY)
    fun setClientAkSk(name: String, credentials: Credentials? = null) {
        setClientAuth(name, AKSK_KEY, credentials)
    }

    fun getClientAk(name: String): String? = getClientAkSk(name)?.userName
    fun setClientAk(name: String, ak: String? = null) {
        setClientAkSk(name, Credentials(ak, getClientSk(name)))
    }

    fun getClientSk(name: String): String? = getClientAkSk(name)?.getPasswordAsString()
    fun setClientSk(name: String, sk: String? = null) {
        setClientAkSk(name, Credentials(getClientAk(name), sk))
    }

    private fun createCredentialAttributes(key: String): CredentialAttributes =
        CredentialAttributes(generateServiceName("${RaccoonPlugin.NAME} Attributes", key))

    private fun createClientAuthCredentialAttributes(name: String, key: String): CredentialAttributes =
        createCredentialAttributes("client.auth.$name.$key")

    private fun getPasswordSafe(attributes: CredentialAttributes): Credentials? =
        kotlin.runCatching { PasswordSafe.instance.get(attributes) }.getOrNull()

    private fun setPasswordSafe(attributes: CredentialAttributes, credentials: Credentials?) {
        PasswordSafe.instance.set(
            attributes,
            credentials?.takeUnless { it.userName.isNullOrBlank() && it.getPasswordAsString().isNullOrBlank() })
    }
}