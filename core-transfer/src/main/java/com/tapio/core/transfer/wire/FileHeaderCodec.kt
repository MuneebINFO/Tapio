package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferError
import java.util.Base64

/**
 * Serialises a [FileHeader] to/from the bytes that precede the file on the wire.
 *
 * Format: a single UTF-8 line of pipe-separated fields, mirroring
 * `core-nfc`'s `SessionTokenCodec` for consistency:
 *
 * ```
 * TXFER1|<base64(displayName)>|<mimeType>|<sizeBytes>
 * ```
 */
object FileHeaderCodec {

    private const val MAGIC = "TXFER1"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 4

    fun encode(header: FileHeader): ByteArray {
        val name = Base64.getEncoder().encodeToString(header.displayName.toByteArray(Charsets.UTF_8))
        return listOf(MAGIC, name, header.mimeType, header.sizeBytes.toString())
            .joinToString(SEPARATOR)
            .toByteArray(Charsets.UTF_8)
    }

    /** @throws TransferError.MalformedStream if the bytes are not a valid header. */
    fun decode(bytes: ByteArray): FileHeader {
        val fields = bytes.toString(Charsets.UTF_8).split(SEPARATOR)
        if (fields.size != FIELD_COUNT || fields[0] != MAGIC) malformed("unexpected structure")

        val displayName = runCatching {
            String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
        }.getOrElse { malformed("display name is not valid Base64") }
        if (displayName.isBlank()) malformed("display name is blank")

        val mimeType = fields[2].ifBlank { malformed("mime type is blank") }

        val sizeBytes = fields[3].toLongOrNull() ?: malformed("size is not a number")
        if (sizeBytes < 0L) malformed("size is negative")

        return FileHeader(displayName, mimeType, sizeBytes)
    }

    private fun malformed(reason: String): Nothing = throw TransferError.MalformedStream(reason)
}
