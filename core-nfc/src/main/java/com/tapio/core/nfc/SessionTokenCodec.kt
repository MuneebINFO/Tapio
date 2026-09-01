package com.tapio.core.nfc

import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import java.util.Base64
import java.util.UUID

/**
 * Serialises a [SessionToken] to/from the compact byte array carried in the NFC
 * exchange.
 *
 * ```
 * TAPIO|<version>|<sessionId>|<wifiDirectMac>|<role>|<issuedAtEpochMs>|<b64(deviceName)>|<b64(payloadSummary)>
 * ```
 *
 * Free-text fields are Base64 so they can hold any character (including `|`). The
 * encoding is hand-rolled and dependency-free: the payload is tiny and the parser
 * stays trivially unit-testable on the JVM.
 */
object SessionTokenCodec {

    private const val MAGIC = "TAPIO"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 8

    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    /** Encodes [token] into the bytes to expose over NFC. */
    fun encode(token: SessionToken): ByteArray = listOf(
        MAGIC,
        token.protocolVersion.toString(),
        token.sessionId.toString(),
        token.wifiDirectMac,
        token.role.name,
        token.issuedAtEpochMs.toString(),
        encodeText(token.deviceName),
        encodeText(token.payloadSummary),
    ).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)

    /**
     * Parses bytes read from a peer back into a [SessionToken].
     *
     * @throws HandshakeError.MalformedPayload if the structure or any field is invalid.
     * @throws HandshakeError.ProtocolMismatch if the version is not [SessionToken.CURRENT_PROTOCOL_VERSION].
     */
    fun decode(bytes: ByteArray): SessionToken {
        val fields = bytes.toString(Charsets.UTF_8).trim().split(SEPARATOR)

        if (fields.size != FIELD_COUNT || fields[0] != MAGIC) malformed("unexpected structure")

        val version = fields[1].toIntOrNull() ?: malformed("version is not a number")
        if (version != SessionToken.CURRENT_PROTOCOL_VERSION) {
            throw HandshakeError.ProtocolMismatch(version, SessionToken.CURRENT_PROTOCOL_VERSION)
        }

        val sessionId = runCatching { UUID.fromString(fields[2]) }
            .getOrElse { malformed("invalid session id") }

        val wifiDirectMac = fields[3]
        if (!MAC_REGEX.matches(wifiDirectMac)) malformed("invalid Wi-Fi Direct MAC address")

        val role = runCatching { HandshakeRole.valueOf(fields[4]) }
            .getOrElse { malformed("unknown role '${fields[4]}'") }

        val issuedAt = fields[5].toLongOrNull() ?: malformed("timestamp is not a number")

        val deviceName = decodeText(fields[6]) { "device name" }
        if (deviceName.isBlank()) malformed("device name is blank")

        val payloadSummary = decodeText(fields[7]) { "payload summary" }
        if (payloadSummary.isBlank()) malformed("payload summary is blank")

        return SessionToken(
            sessionId = sessionId,
            wifiDirectMac = wifiDirectMac,
            deviceName = deviceName,
            payloadSummary = payloadSummary,
            role = role,
            issuedAtEpochMs = issuedAt,
            protocolVersion = version,
        )
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private inline fun decodeText(value: String, field: () -> String): String =
        runCatching { String(Base64.getDecoder().decode(value), Charsets.UTF_8) }
            .getOrElse { malformed("${field()} is not valid Base64") }

    private fun malformed(reason: String): Nothing = throw HandshakeError.MalformedPayload(reason)
}
