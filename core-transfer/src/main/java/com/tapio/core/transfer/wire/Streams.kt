package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.TransferError
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads exactly [count] bytes, blocking until they arrive.
 *
 * `InputStream.readNBytes` would do this but is API 33+; Tapio targets API 26.
 *
 * @throws TransferError.MalformedStream if the stream ends early.
 */
internal fun InputStream.readFully(count: Int): ByteArray {
    val out = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(out, offset, count - offset)
        if (read < 0) {
            throw TransferError.MalformedStream("expected $count bytes, stream ended after $offset")
        }
        offset += read
    }
    return out
}

/** Reads a big-endian 32-bit length prefix. */
internal fun InputStream.readInt32(): Int {
    val b = readFully(Int.SIZE_BYTES)
    return (b[0].toInt() and 0xFF shl 24) or
        (b[1].toInt() and 0xFF shl 16) or
        (b[2].toInt() and 0xFF shl 8) or
        (b[3].toInt() and 0xFF)
}

/** Writes a big-endian 32-bit length prefix. */
internal fun OutputStream.writeInt32(value: Int) {
    write(
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        ),
    )
}
