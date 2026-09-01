package com.tapio.core.nfc.domain

import java.util.UUID

/**
 * The payload two devices swap over NFC the instant they touch.
 *
 * NFC is used **only** as a handshake channel — its throughput is far too low for
 * photos or video. This token carries just enough for the receiver to (a) decide
 * whether to accept, and (b) join the sender's Wi-Fi Direct group, over which the
 * real content is streamed.
 *
 * @property sessionId unique id correlating the NFC handshake with the later Wi-Fi Direct transfer.
 * @property wifiDirectMac MAC address of the peer's Wi-Fi Direct interface, formatted `AA:BB:CC:DD:EE:FF`.
 * @property deviceName human-readable name shown to the other user ("Marie's Pixel").
 * @property payloadSummary short line the receiver sees in the accept prompt — e.g.
 *   "Une photo · 2,3 Mo" or "Un contact". Never the payload itself.
 * @property role which side of the exchange minted this token.
 * @property protocolVersion wire-format version; a receiver rejects tokens it does not understand.
 * @property issuedAtEpochMs creation time, used to expire stale tokens.
 */
data class SessionToken(
    val sessionId: UUID,
    val wifiDirectMac: String,
    val deviceName: String,
    val payloadSummary: String,
    val role: HandshakeRole,
    val issuedAtEpochMs: Long,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
) {
    init {
        require(deviceName.isNotBlank()) { "deviceName must not be blank" }
        require(payloadSummary.isNotBlank()) { "payloadSummary must not be blank" }
    }

    companion object {
        /** Bump whenever [com.tapio.core.nfc.SessionTokenCodec]'s format changes incompatibly. */
        const val CURRENT_PROTOCOL_VERSION: Int = 2
    }
}

/** Which device produced a given [SessionToken]. */
enum class HandshakeRole {
    /** Holds the content and initiates the Wi-Fi Direct group. */
    SENDER,

    /** Joins the sender's group and receives the content. */
    RECEIVER,
}
