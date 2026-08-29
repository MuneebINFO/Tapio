package com.tapio.core.nfc.apdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduProtocolTest {

    @Test
    fun `select apdu is recognised and its AID extracted`() {
        val apdu = ApduProtocol.buildSelectApdu()

        assertTrue(ApduProtocol.isSelectApdu(apdu))
        assertArrayEquals(ApduProtocol.TAPIO_AID, ApduProtocol.selectedAid(apdu))
    }

    @Test
    fun `selectedAid returns null for a non-select apdu`() {
        assertNull(ApduProtocol.selectedAid(ApduProtocol.buildReadTokenApdu()))
    }

    @Test
    fun `selectedAid returns null when the declared length overruns the buffer`() {
        val truncated = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0x01, 0x02)

        assertNull(ApduProtocol.selectedAid(truncated))
    }

    @Test
    fun `read-token apdu is recognised`() {
        assertTrue(ApduProtocol.isReadTokenApdu(ApduProtocol.buildReadTokenApdu()))
        assertFalse(ApduProtocol.isReadTokenApdu(ApduProtocol.buildSelectApdu()))
    }

    @Test
    fun `withStatus then parseResponse round-trips payload and status`() {
        val payload = byteArrayOf(1, 2, 3, 4)

        val response = ApduProtocol.parseResponse(ApduProtocol.withStatus(payload))

        assertArrayEquals(payload, response.payload)
        assertTrue(response.isSuccess)
    }

    @Test
    fun `parseResponse flags a non-success status word`() {
        val response = ApduProtocol.parseResponse(
            ApduProtocol.withStatus(ByteArray(0), ApduProtocol.STATUS_NOT_FOUND),
        )

        assertFalse(response.isSuccess)
        assertEquals(0, response.payload.size)
    }

    @Test
    fun `parseResponse rejects a buffer shorter than a status word`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApduProtocol.parseResponse(byteArrayOf(0x90.toByte()))
        }
    }

    @Test
    fun `ApduResponse equality is value-based`() {
        val a = ApduResponse(byteArrayOf(1, 2), byteArrayOf(0x90.toByte(), 0x00))
        val b = ApduResponse(byteArrayOf(1, 2), byteArrayOf(0x90.toByte(), 0x00))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
