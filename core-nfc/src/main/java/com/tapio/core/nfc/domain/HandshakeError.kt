package com.tapio.core.nfc.domain

/**
 * Every way an NFC handshake can fail, as a closed set so callers can exhaustively
 * map each case to user-facing copy.
 */
sealed class HandshakeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** NFC is switched off in system settings. */
    data object NfcDisabled : HandshakeError("NFC is turned off")

    /** The device has no NFC hardware. */
    data object NfcUnsupported : HandshakeError("This device has no NFC hardware")

    /** The phones were separated before the exchange completed. */
    data object TagLost : HandshakeError("The phones moved apart before the handshake finished")

    /** Bytes were received but could not be parsed into a [SessionToken]. */
    data class MalformedPayload(val reason: String) :
        HandshakeError("Malformed handshake payload: $reason")

    /** The peer speaks a protocol version this build does not support. */
    data class ProtocolMismatch(val received: Int, val supported: Int) :
        HandshakeError("Peer uses handshake protocol v$received, this app supports v$supported")

    /** Low-level NFC I/O failure. */
    data class Io(val ioCause: Throwable) :
        HandshakeError("NFC communication failed", ioCause)
}
