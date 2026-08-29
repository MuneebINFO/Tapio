package com.tapio.core.nfc

import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class SessionTokenCodecTest {

    private val sample = SessionToken(
        sessionId = UUID.fromString("00000000-0000-0000-0000-0000000000ab"),
        wifiDirectMac = "A1:B2:C3:D4:E5:F6",
        deviceName = "Marie's Pixel | 8",
        role = HandshakeRole.SENDER,
        issuedAtEpochMs = 1_724_900_000_000,
    )

    @Test
    fun `encode then decode round-trips every field`() {
        val decoded = SessionTokenCodec.decode(SessionTokenCodec.encode(sample))

        assertEquals(sample, decoded)
    }

    @Test
    fun `device name survives separators and unicode`() {
        val tricky = sample.copy(deviceName = "N|cé's ☎ phone")

        val decoded = SessionTokenCodec.decode(SessionTokenCodec.encode(tricky))

        assertEquals("N|cé's ☎ phone", decoded.deviceName)
    }

    @Test
    fun `unknown magic prefix is rejected`() {
        val error = assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("NOPE|1|x|y|z|0|AA==".toByteArray())
        }
        assertEquals("Malformed handshake payload: unexpected structure", error.message)
    }

    @Test
    fun `wrong field count is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("TAPIO|1|only-three|fields".toByteArray())
        }
    }

    @Test
    fun `future protocol version raises ProtocolMismatch`() {
        val bytes = SessionTokenCodec.encode(sample)
            .toString(Charsets.UTF_8)
            .replaceFirst("TAPIO|1|", "TAPIO|99|")
            .toByteArray()

        val error = assertThrows(HandshakeError.ProtocolMismatch::class.java) {
            SessionTokenCodec.decode(bytes)
        }
        assertEquals(99, error.received)
        assertEquals(SessionToken.CURRENT_PROTOCOL_VERSION, error.supported)
    }

    @Test
    fun `malformed MAC address is rejected`() {
        val bytes = SessionTokenCodec.encode(sample.copy(wifiDirectMac = "A1:B2:C3:D4:E5:F6"))
            .toString(Charsets.UTF_8)
            .replace("A1:B2:C3:D4:E5:F6", "not-a-mac")
            .toByteArray()

        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode(bytes)
        }
    }

    @Test
    fun `invalid session id is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("TAPIO|1|not-a-uuid|A1:B2:C3:D4:E5:F6|SENDER|0|QQ==".toByteArray())
        }
    }

    @Test
    fun `unknown role is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode(
                "TAPIO|1|00000000-0000-0000-0000-0000000000ab|A1:B2:C3:D4:E5:F6|MIDDLE|0|QQ=="
                    .toByteArray(),
            )
        }
    }
}
