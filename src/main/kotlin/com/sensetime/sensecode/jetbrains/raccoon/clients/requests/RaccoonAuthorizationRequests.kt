package com.sensetime.sensecode.jetbrains.raccoon.clients.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


private fun encrypt(src: ByteArray): String = Cipher.getInstance("AES/CFB/NoPadding").let { cipher ->
    cipher.init(
        Cipher.ENCRYPT_MODE,
        byteArrayOf(
            115, 101, 110, 115, 101, 114, 97, 99, 99, 111, 111, 110, 50, 48, 50, 51
        ).let { pwd -> SecretKeySpec(pwd, "AES").also { Arrays.fill(pwd, 0) } })
    Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(src))
}

private fun encryptRawPhoneNumber(src: String): String = encrypt(src.toByteArray())
private fun encryptPassword(src: CharArray): String =
    ByteArray(src.size) { src[it].code.toByte() }.also { Arrays.fill(src, '0') }
        .let { pwd -> encrypt(pwd).also { Arrays.fill(pwd, 0) } }

@Serializable
internal data class RaccoonClientLoginWithPhoneBody(
    @SerialName("nation_code")
    val nationCode: String,
    @SerialName("phone")
    val encryptedPhone: String,
    @SerialName("password")
    val encryptedPassword: String
) {
    constructor(nationCode: String, rawPhoneNumber: String, rawPassword: CharArray) : this(
        nationCode, encryptRawPhoneNumber(rawPhoneNumber), encryptPassword(rawPassword)
    )
}

@Serializable
internal data class RaccoonClientLoginWithSMSBody(
    @SerialName("nation_code")
    val nationCode: String,
    @SerialName("phone")
    val encryptedPhone: String,
    @SerialName("sms_code")
    val smsCode: String
) {
    constructor(nationCode: String, phoneNumber: String, smsCode: String, isRaw: Boolean) : this(
        nationCode, if (isRaw) encryptRawPhoneNumber(phoneNumber) else phoneNumber, smsCode
    )
}

@Serializable
internal data class RaccoonClientLoginWithEmailBody(
    val email: String,
    @SerialName("password")
    val encryptedPassword: String
) {
    constructor(email: String, rawPassword: CharArray) : this(email, encryptPassword(rawPassword))
}

@Serializable
internal data class RaccoonClientRefreshTokenRequest(
    @SerialName("refresh_token")
    private val refreshToken: String
)

@Serializable
internal data class RaccoonClientSendSMSRequest(
    @SerialName("captcha_result")
    private val captchaString: String,
    @SerialName("captcha_uuid")
    private val captchaUUID: String,
    @SerialName("nation_code")
    val nationCode: String,
    @SerialName("phone")
    val encryptedPhone: String
) {
    constructor(
        captchaString: String,
        captchaUUID: String,
        nationCode: String,
        phoneNumber: String,
        isRaw: Boolean
    ) : this(
        captchaString, captchaUUID, nationCode, if (isRaw) encryptRawPhoneNumber(phoneNumber) else phoneNumber
    )
}
