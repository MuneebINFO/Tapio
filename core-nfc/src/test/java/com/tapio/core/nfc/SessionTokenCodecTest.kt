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
        wifiSsid = "DIRECT-aH-Tapio | Pixel",
        wifiPassphrase = "correcthorse42",
        deviceName = "Marie's Pixel | 8",
        payloadSummary = "Une photo · 2,3 Mo",
        role = HandshakeRole.SENDER,
        issuedAtEpochMs = 1_724_900_000_000,
    )

    private val v = SessionToken.CURRENT_PROTOCOL_VERSION

    @Test
    fun `encode then decode round-trips every field`() {
        assertEquals(sample, SessionTokenCodec.decode(SessionTokenCodec.encode(sample)))
    }

    @Test
    fun `free-text fields survive separators and unicode`() {
        val tricky = sample.copy(
            deviceName = "N|cé's ☎ phone",
            payloadSummary = "Un | contact ✦",
            wifiSsid = "DIRECT-x|y ✦",
        )

        val decoded = SessionTokenCodec.decode(SessionTokenCodec.encode(tricky))

        assertEquals(tricky, decoded)
    }

    @Test
    fun `unknown magic prefix is rejected`() {
        val error = assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("NOPE|$v|x|y|z|SENDER|0|AA==|AA==".toByteArray())
        }
        assertEquals("Malformed handshake payload: unexpected structure", error.message)
    }

    @Test
    fun `wrong field count is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("TAPIO|$v|only-three|fields".toByteArray())
        }
    }

    @Test
    fun `future protocol version raises ProtocolMismatch`() {
        val bytes = SessionTokenCodec.encode(sample)
            .toString(Charsets.UTF_8)
            .replaceFirst("TAPIO|$v|", "TAPIO|99|")
            .toByteArray()

        val error = assertThrows(HandshakeError.ProtocolMismatch::class.java) {
            SessionTokenCodec.decode(bytes)
        }
        assertEquals(99, error.received)
        assertEquals(v, error.supported)
    }

    @Test
    fun `invalid session id is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode("TAPIO|$v|not-a-uuid|RElSRUNU|cGFzc3dvcmQ4|SENDER|0|QQ==|QQ==".toByteArray())
        }
    }

    @Test
    fun `unknown role is rejected`() {
        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode(
                "TAPIO|$v|00000000-0000-0000-0000-0000000000ab|RElSRUNU|cGFzc3dvcmQ4|MIDDLE|0|QQ==|QQ=="
                    .toByteArray(),
            )
        }
    }

    @Test
    fun `a too-short passphrase is rejected`() {
        // fields: magic|v|uuid|b64("DIRECT-x")|b64("short")|role|ts|b64("Dev")|b64("Sum")
        val raw = "TAPIO|$v|00000000-0000-0000-0000-0000000000ab|RElSRUNULXg=|c2hvcnQ=|SENDER|0|RGV2|U3Vt"

        assertThrows(HandshakeError.MalformedPayload::class.java) {
            SessionTokenCodec.decode(raw.toByteArray())
        }
    }
}
