package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileHeaderCodecTest {

    @Test
    fun `encode then decode round-trips, separators and unicode included`() {
        val header = FileHeader("holiday | 2026 ☀.mp4", "video/mp4", 1_234_567)

        val decoded = FileHeaderCodec.decode(FileHeaderCodec.encode(header))

        assertEquals(header, decoded)
    }

    @Test
    fun `unknown magic is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            FileHeaderCodec.decode("NOPE|QQ==|image/jpeg|1".toByteArray())
        }
    }

    @Test
    fun `wrong field count is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            FileHeaderCodec.decode("TXFER1|QQ==|image/jpeg".toByteArray())
        }
    }

    @Test
    fun `non-numeric size is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            FileHeaderCodec.decode("TXFER1|QQ==|image/jpeg|big".toByteArray())
        }
    }

    @Test
    fun `negative size is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            FileHeaderCodec.decode("TXFER1|QQ==|image/jpeg|-5".toByteArray())
        }
    }

    @Test
    fun `blank display name is rejected`() {
        // Base64 of an empty string is an empty string -> blank name.
        assertThrows(TransferError.MalformedStream::class.java) {
            FileHeaderCodec.decode("TXFER1||image/jpeg|1".toByteArray())
        }
    }
}
