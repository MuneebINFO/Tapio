package com.tapio.core.transfer.wire

import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import java.util.Base64

/**
 * Serialises a [ContentHeader] to/from the bytes that precede the payload on the wire.
 *
 * ```
 * TXFER2|<contentKind>|<base64(displayName)>|<mimeType>|<sizeBytes>
 * ```
 */
object ContentHeaderCodec {

    private const val MAGIC = "TXFER2"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 5

    fun encode(header: ContentHeader): ByteArray {
        val name = Base64.getEncoder().encodeToString(header.displayName.toByteArray(Charsets.UTF_8))
        return listOf(MAGIC, header.contentKind.name, name, header.mimeType, header.sizeBytes.toString())
            .joinToString(SEPARATOR)
            .toByteArray(Charsets.UTF_8)
    }

    /** @throws TransferError.MalformedStream if the bytes are not a valid header. */
    fun decode(bytes: ByteArray): ContentHeader {
        val fields = bytes.toString(Charsets.UTF_8).split(SEPARATOR)
        if (fields.size != FIELD_COUNT || fields[0] != MAGIC) malformed("unexpected structure")

        val contentKind = runCatching { ContentKind.valueOf(fields[1]) }
            .getOrElse { malformed("unknown content kind '${fields[1]}'") }

        val displayName = runCatching {
            String(Base64.getDecoder().decode(fields[2]), Charsets.UTF_8)
        }.getOrElse { malformed("display name is not valid Base64") }
        if (displayName.isBlank()) malformed("display name is blank")

        val mimeType = fields[3].ifBlank { malformed("mime type is blank") }

        val sizeBytes = fields[4].toLongOrNull() ?: malformed("size is not a number")
        if (sizeBytes < 0L) malformed("size is negative")

        return ContentHeader(contentKind, displayName, mimeType, sizeBytes)
    }

    private fun malformed(reason: String): Nothing = throw TransferError.MalformedStream(reason)
}
