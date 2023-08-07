package com.sensetime.sensecore.sensecodeplugin.security

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe


object GptMentorCredentialsManager {
    fun getAccessKey(): String? {
        return getPasswordSafe(apiAccessKey)
    }

    fun setAccessKey(password: String?) {
        setPasswordSafe(apiAccessKey, password)
    }

    fun getSecretKey(): String? {
        return getPasswordSafe(apiSecretKey)
    }

    fun setSecretKey(password: String?) {
        setPasswordSafe(apiSecretKey, password)
    }


    private val apiAccessKey = createCredentialAttributes("SENSE_CORE_ACCESS_KEY")
    private val apiSecretKey = createCredentialAttributes("SENSE_CORE_SECRET_KEY")

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("SenseCodePasswordManager", key))
    }

    private fun getPasswordSafe(attributes: CredentialAttributes): String? {
        return try {
            PasswordSafe.instance.getPassword(attributes)
        } catch (e: Exception) {
            null
        }
    }

    private fun setPasswordSafe(attributes: CredentialAttributes, password: String?) {
        PasswordSafe.instance.setPassword(attributes, if (password.isNullOrEmpty()) null else password)
    }
}
