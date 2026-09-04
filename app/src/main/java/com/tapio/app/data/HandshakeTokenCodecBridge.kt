package com.tapio.app.data

import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.domain.SessionToken
import java.util.Base64

/** Carries a [SessionToken] through an `Intent` extra as Base64 text. */
object HandshakeTokenCodecBridge {

    fun encode(token: SessionToken): String =
        Base64.getEncoder().encodeToString(SessionTokenCodec.encode(token))

    fun decode(base64: String?): SessionToken? = base64?.let {
        runCatching { SessionTokenCodec.decode(Base64.getDecoder().decode(it)) }.getOrNull()
    }
}
