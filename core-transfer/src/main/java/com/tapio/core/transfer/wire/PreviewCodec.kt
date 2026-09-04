package com.tapio.core.transfer.wire

import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.domain.TransferError
import java.util.Base64

/**
 * Serialises a [ContentPreview]: a UTF-8 metadata line, a newline, then the raw
 * thumbnail bytes.
 *
 * ```
 * TPREV1|<kind>|<b64(displayName)>|<mimeType>|<sizeBytes>|<thumbnailByteCount>\n<thumbnail…>
 * ```
 */
object PreviewCodec {

    private const val MAGIC = "TPREV1"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 6
    private const val NEWLINE = '\n'.code.toByte()

    fun encode(preview: ContentPreview): ByteArray {
        val thumb = preview.thumbnailJpeg ?: ByteArray(0)
        val meta = listOf(
            MAGIC,
            preview.contentKind.name,
            Base64.getEncoder().encodeToString(preview.displayName.toByteArray(Charsets.UTF_8)),
            preview.mimeType,
            preview.sizeBytes.toString(),
            thumb.size.toString(),
        ).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)

        return meta + NEWLINE + thumb
    }

    /** @throws TransferError.MalformedStream if [bytes] are not a valid preview. */
    fun decode(bytes: ByteArray): ContentPreview {
        val newline = bytes.indexOf(NEWLINE)
        if (newline < 0) malformed("no metadata line")

        val fields = String(bytes, 0, newline, Charsets.UTF_8).split(SEPARATOR)
        if (fields.size != FIELD_COUNT || fields[0] != MAGIC) malformed("unexpected structure")

        val kind = runCatching { ContentKind.valueOf(fields[1]) }
            .getOrElse { malformed("unknown content kind '${fields[1]}'") }
        val displayName = runCatching {
            String(Base64.getDecoder().decode(fields[2]), Charsets.UTF_8)
        }.getOrElse { malformed("display name is not valid Base64") }
        if (displayName.isBlank()) malformed("display name is blank")

        val mimeType = fields[3].ifBlank { malformed("mime type is blank") }
        val sizeBytes = fields[4].toLongOrNull()?.takeIf { it >= 0L } ?: malformed("invalid size")
        val thumbLen = fields[5].toIntOrNull()?.takeIf { it >= 0 } ?: malformed("invalid thumbnail length")

        val thumbStart = newline + 1
        if (bytes.size < thumbStart + thumbLen) malformed("truncated thumbnail")
        val thumbnail = if (thumbLen > 0) bytes.copyOfRange(thumbStart, thumbStart + thumbLen) else null

        return ContentPreview(kind, displayName, mimeType, sizeBytes, thumbnail)
    }

    private fun malformed(reason: String): Nothing = throw TransferError.MalformedStream("preview: $reason")
}
