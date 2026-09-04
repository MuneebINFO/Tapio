package com.tapio.core.transfer.wire

import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.domain.TransferError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewCodecTest {

    @Test
    fun `round-trips a file preview with a thumbnail`() {
        val thumb = ByteArray(1234) { (it % 256).toByte() }
        val preview = ContentPreview(ContentKind.FILE, "holiday | 2026.jpg", "image/jpeg", 4_200_000, thumb)

        val decoded = PreviewCodec.decode(PreviewCodec.encode(preview))

        assertEquals("holiday | 2026.jpg", decoded.displayName)
        assertEquals(4_200_000L, decoded.sizeBytes)
        assertEquals(ContentKind.FILE, decoded.contentKind)
        assertArrayEquals(thumb, decoded.thumbnailJpeg)
    }

    @Test
    fun `round-trips a contact preview with no thumbnail`() {
        val preview = ContentPreview(ContentKind.CONTACT, "Jean Dupont", "application/vnd.tapio.contact", 0, null)

        val decoded = PreviewCodec.decode(PreviewCodec.encode(preview))

        assertEquals("Jean Dupont", decoded.displayName)
        assertNull(decoded.thumbnailJpeg)
    }

    @Test
    fun `a thumbnail containing newline bytes still round-trips`() {
        val thumb = byteArrayOf(0x0A, 0x00, 0x0A, 0x0A, 0x7F, 0x0A)
        val preview = ContentPreview(ContentKind.FILE, "x.png", "image/png", 10, thumb)

        assertArrayEquals(thumb, PreviewCodec.decode(PreviewCodec.encode(preview)).thumbnailJpeg)
    }

    @Test
    fun `rejects a malformed preview`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            PreviewCodec.decode("NOPE|FILE|QQ==|image/jpeg|1|0\n".toByteArray())
        }
    }

    @Test
    fun `rejects a truncated thumbnail`() {
        assertThrows(TransferError.MalformedStream::class.java) {
            PreviewCodec.decode("TPREV1|FILE|QQ==|image/jpeg|1|99\nshort".toByteArray())
        }
    }
}
