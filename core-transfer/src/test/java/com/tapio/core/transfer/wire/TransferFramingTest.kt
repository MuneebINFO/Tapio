package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class TransferFramingTest {

    @Test
    fun `writeHeader then readHeader round-trips`() {
        val header = FileHeader("photo.jpg", "image/jpeg", 4096)
        val buffer = ByteArrayOutputStream().apply { TransferFraming.writeHeader(this, header) }

        assertEquals(header, TransferFraming.readHeader(buffer.toByteArray().inputStream()))
    }

    @Test
    fun `an out-of-range length prefix is rejected before allocating`() {
        val hostile = ByteArrayOutputStream().apply {
            writeInt32(64 * 1024 * 1024)
            write(ByteArray(8))
        }

        assertThrows(TransferError.MalformedStream::class.java) {
            TransferFraming.readHeader(hostile.toByteArray().inputStream())
        }
    }

    @Test
    fun `a truncated header is rejected`() {
        val full = ByteArrayOutputStream().apply {
            TransferFraming.writeHeader(this, FileHeader("photo.jpg", "image/jpeg", 4096))
        }.toByteArray()

        assertThrows(TransferError.MalformedStream::class.java) {
            TransferFraming.readHeader(full.copyOf(full.size - 3).inputStream())
        }
    }
}
