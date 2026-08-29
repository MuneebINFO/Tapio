package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.Checksum
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 helpers used to verify file integrity end-to-end. */
object Sha256 {

    private const val ALGORITHM = "SHA-256"

    /** Length, in bytes, of a SHA-256 digest — the size of the checksum trailer. */
    const val SIZE_BYTES: Int = 32

    private const val STREAM_BUFFER_BYTES = 8 * 1024

    fun newDigest(): MessageDigest = MessageDigest.getInstance(ALGORITHM)

    fun of(bytes: ByteArray): Checksum = Checksum(newDigest().digest(bytes))

    /** Consumes [input] to the end and returns its digest. Does not close the stream. */
    fun of(input: InputStream): Checksum {
        val digest = newDigest()
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return Checksum(digest.digest())
    }
}
