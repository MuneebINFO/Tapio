package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import java.io.InputStream
import java.io.OutputStream

/**
 * The on-the-wire layout of a whole transfer:
 *
 * ```
 * ┌────────────┬───────────────┬───────────────────┬──────────────────────┐
 * │ int32 len  │ header (len)  │ file bytes (size) │ SHA-256 trailer (32) │
 * └────────────┴───────────────┴───────────────────┴──────────────────────┘
 * ```
 *
 * `size` comes from the header, so the receiver always knows exactly how many
 * file bytes to read before the trailer.
 */
object TransferFraming {

    /** Guards against a corrupt length prefix causing a huge allocation. */
    const val MAX_HEADER_BYTES: Int = 8 * 1024

    fun writeHeader(out: OutputStream, header: ContentHeader) {
        val body = ContentHeaderCodec.encode(header)
        out.writeInt32(body.size)
        out.write(body)
    }

    /** @throws TransferError.MalformedStream on a bad length prefix or truncated header. */
    fun readHeader(input: InputStream): ContentHeader {
        val length = input.readInt32()
        if (length !in 1..MAX_HEADER_BYTES) {
            throw TransferError.MalformedStream("header length $length outside 1..$MAX_HEADER_BYTES")
        }
        return ContentHeaderCodec.decode(input.readFully(length))
    }

    fun readChecksumTrailer(input: InputStream): ByteArray = input.readFully(Sha256.SIZE_BYTES)
}
