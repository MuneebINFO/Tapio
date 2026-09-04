package com.tapio.core.nfc.domain

import java.util.UUID

/**
 * The payload two devices swap over NFC the instant they touch.
 *
 * NFC is used **only** as a handshake channel. This token carries just enough for
 * the receiver to (a) decide whether to accept, and (b) join the Wi-Fi Direct
 * group the sender has already created, over which the real content is streamed.
 *
 * @property sessionId unique id correlating the NFC handshake with the transfer.
 * @property wifiSsid the sender's Wi-Fi Direct group name (always starts with `DIRECT-`).
 * @property wifiPassphrase the group passphrase (8–63 chars).
 * @property deviceName human-readable sender name shown to the other user.
 * @property payloadSummary short line the receiver sees in the accept prompt — never the payload.
 * @property role which side of the exchange minted this token.
 * @property issuedAtEpochMs creation time, used to expire stale tokens.
 * @property protocolVersion wire-format version; a receiver rejects tokens it does not understand.
 */
data class SessionToken(
    val sessionId: UUID,
    val wifiSsid: String,
    val wifiPassphrase: String,
    val deviceName: String,
    val payloadSummary: String,
    val role: HandshakeRole,
    val issuedAtEpochMs: Long,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
) {
    init {
        require(wifiSsid.isNotBlank()) { "wifiSsid must not be blank" }
        require(wifiPassphrase.length in PASSPHRASE_RANGE) {
            "wifiPassphrase must be ${PASSPHRASE_RANGE.first}–${PASSPHRASE_RANGE.last} characters"
        }
        require(deviceName.isNotBlank()) { "deviceName must not be blank" }
        require(payloadSummary.isNotBlank()) { "payloadSummary must not be blank" }
    }

    companion object {
        /** Bump whenever [com.tapio.core.nfc.SessionTokenCodec]'s format changes incompatibly. */
        const val CURRENT_PROTOCOL_VERSION: Int = 3

        val PASSPHRASE_RANGE: IntRange = 8..63
    }
}

/** Which device produced a given [SessionToken]. */
enum class HandshakeRole {
    /** Holds the content and owns the Wi-Fi Direct group. */
    SENDER,

    /** Joins the sender's group and receives the content. */
    RECEIVER,
}
