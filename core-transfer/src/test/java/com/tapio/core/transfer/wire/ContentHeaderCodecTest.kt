package com.tapio.core.transfer.wire

import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentHeaderCodecTest {

    @Test
    fun `round-trips a file header, separators and unicode included`() {
        val header = ContentHeader(ContentKind.FILE, "holiday | 2026 ☀.mp4", "video/mp4", 1_234_567)

        assertEquals(header, ContentHeaderCodec.decode(ContentHeaderCodec.encode(header)))
    }

    @Test
    fun `round-trips a contact header`() {
        val header = ContentHeader(ContentKind.CONTACT, "Jean Dupont", "application/vnd.tapio.contact", 88)

        assertEquals(header, ContentHeaderCodec.decode(ContentHeaderCodec.encode(header)))
    }

    @Test
    fun `unknown magic is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("NOPE|FILE|QQ==|image/jpeg|1".toByteArray())
        }
    }

    @Test
    fun `unknown content kind is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("TXFER2|WIDGET|QQ==|image/jpeg|1".toByteArray())
        }
    }

    @Test
    fun `wrong field count is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("TXFER2|FILE|QQ==|image/jpeg".toByteArray())
        }
    }

    @Test
    fun `non-numeric size is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("TXFER2|FILE|QQ==|image/jpeg|big".toByteArray())
        }
    }

    @Test
    fun `negative size is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("TXFER2|FILE|QQ==|image/jpeg|-5".toByteArray())
        }
    }

    @Test
    fun `blank display name is rejected`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            ContentHeaderCodec.decode("TXFER2|FILE||image/jpeg|1".toByteArray())
        }
    }
}
