package com.tapio.core.transfer.domain

import com.tapio.core.common.ContentKind

/**
 * What the receiver sees **before** accepting: the real file name and size, and —
 * for a photo or video — a small JPEG thumbnail the sender generated.
 *
 * This is not the payload: declining here means the file bytes are never sent.
 * For a contact card only the name is previewed; the number arrives after accept.
 */
class ContentPreview(
    val contentKind: ContentKind,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    thumbnailJpeg: ByteArray?,
) {
    /** A small JPEG, or `null` for contacts and when no thumbnail could be made. */
    val thumbnailJpeg: ByteArray? = thumbnailJpeg?.copyOf()

    override fun toString(): String =
        "ContentPreview($contentKind, '$displayName', $sizeBytes B, thumb=${thumbnailJpeg?.size ?: 0} B)"
}
