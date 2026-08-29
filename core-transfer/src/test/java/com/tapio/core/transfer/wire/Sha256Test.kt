package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.Checksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `digest of abc matches the published SHA-256 vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray()).hex,
        )
    }

    @Test
    fun `streaming digest equals one-shot digest`() {
        val bytes = ByteArray(10_000) { it.toByte() }

        assertEquals(Sha256.of(bytes), Sha256.of(bytes.inputStream()))
    }

    @Test
    fun `trailer size constant is 32 bytes`() {
        assertEquals(32, Sha256.SIZE_BYTES)
        assertEquals(Sha256.SIZE_BYTES, Sha256.of(ByteArray(0)).bytes.size)
    }

    @Test
    fun `checksum hex round-trips through ofHex`() {
        val checksum = Sha256.of("tapio".toByteArray())

        assertEquals(checksum, Checksum.ofHex(checksum.hex))
    }

    @Test
    fun `checksum equality is value-based`() {
        assertEquals(Checksum(byteArrayOf(1, 2, 3)), Checksum(byteArrayOf(1, 2, 3)))
        assertNotEquals(Checksum(byteArrayOf(1)), Checksum(byteArrayOf(2)))
    }
}
