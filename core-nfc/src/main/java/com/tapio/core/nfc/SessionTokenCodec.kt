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
 * The format is a single UTF-8 line of pipe-separated fields:
 *
 * ```
 * TAPIO|<version>|<sessionId>|<wifiDirectMac>|<role>|<issuedAtEpochMs>|<base64(deviceName)>
 * ```
 *
 * `deviceName` is Base64-encoded so it can contain any character (including `|`)
 * without escaping. The encoding is deliberately hand-rolled and dependency-free:
 * the payload is tiny and the parser stays trivially unit-testable on the JVM.
 */
object SessionTokenCodec {

    private const val MAGIC = "TAPIO"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 7

    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    /** Encodes [token] into the bytes to expose over NFC. */
    fun encode(token: SessionToken): ByteArray {
        val deviceName = Base64.getEncoder()
            .encodeToString(token.deviceName.toByteArray(Charsets.UTF_8))

        return listOf(
            MAGIC,
            token.protocolVersion.toString(),
            token.sessionId.toString(),
            token.wifiDirectMac,
            token.role.name,
            token.issuedAtEpochMs.toString(),
            deviceName,
        ).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)
    }

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

        val deviceName = runCatching {
            String(Base64.getDecoder().decode(fields[6]), Charsets.UTF_8)
        }.getOrElse { malformed("device name is not valid Base64") }
        if (deviceName.isBlank()) malformed("device name is blank")

        return SessionToken(
            sessionId = sessionId,
            wifiDirectMac = wifiDirectMac,
            deviceName = deviceName,
            role = role,
            issuedAtEpochMs = issuedAt,
            protocolVersion = version,
        )
    }

    private fun malformed(reason: String): Nothing = throw HandshakeError.MalformedPayload(reason)
}
