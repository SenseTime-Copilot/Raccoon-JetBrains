package com.sensetime.sensecore.sensecodeplugin.clients

import com.intellij.credentialStore.Credentials
import com.sensetime.sensecore.sensecodeplugin.settings.SenseCodeCredentialsManager
import com.sensetime.sensecore.sensecodeplugin.settings.letIfFilled

abstract class AkSkCodeClient : CodeClient() {
    override val userName: String?
        get() = aksk.letIfFilled { _, _ -> "$name ak/sk user" }

    protected fun akGetter() = SenseCodeCredentialsManager.getClientAk(name) ?: ""
    protected fun akSetter(ak: String) {
        SenseCodeCredentialsManager.setClientAk(name, ak)
    }

    protected fun skGetter() = SenseCodeCredentialsManager.getClientSk(name) ?: ""
    protected fun skSetter(sk: String) {
        SenseCodeCredentialsManager.setClientSk(name, sk)
    }

    protected val aksk: Credentials?
        get() = SenseCodeCredentialsManager.getClientAkSk(name)
}