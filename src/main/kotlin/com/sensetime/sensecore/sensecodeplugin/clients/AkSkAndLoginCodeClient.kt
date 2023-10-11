package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.credentialStore.Credentials
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeCredentialsManager
import com.sensetime.sensecore.sensecodeplugin.settings.letIfFilled

abstract class AkSkAndLoginCodeClient : AkSkCodeClient() {
    override val userName: String?
        get() = accessToken.letIfFilled { user, _ -> user } ?: super.userName

    override val alreadyLoggedIn: Boolean
        get() = accessToken.letIfFilled { _, _ -> true } ?: false

    override val isSupportLogin: Boolean = true

    protected fun clearLoginToken() {
        SenseCodeCredentialsManager.setClientAuth(name, ACCESS_TOKEN_KEY)
        SenseCodeCredentialsManager.setClientAuth(name, REFRESH_TOKEN_KEY)
    }

    protected val refreshToken: Credentials?
        get() = SenseCodeCredentialsManager.getClientAuth(name, REFRESH_TOKEN_KEY)
    protected val accessToken: Credentials?
        get() = SenseCodeCredentialsManager.getClientAuth(name, ACCESS_TOKEN_KEY)

    companion object {
        const val REFRESH_TOKEN_KEY = "refreshToken"
        const val ACCESS_TOKEN_KEY = "accessToken"
    }
}