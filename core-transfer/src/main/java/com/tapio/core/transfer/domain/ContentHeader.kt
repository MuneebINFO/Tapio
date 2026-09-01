package com.tapio.core.transfer.domain

import com.tapio.core.common.ContentKind

/**
 * The metadata the sender transmits before the payload bytes, so the receiver
 * knows what is coming and can create the right kind of destination.
 *
 * The integrity checksum is **not** here — it travels as a trailer after the bytes
 * so both sides can compute it in a single streaming pass.
 *
 * @property contentKind whether the bytes are a file or a serialised contact card.
 * @property displayName file name, or the name a contact should be saved as.
 * @property mimeType `image/jpeg`, `video/mp4`, or `application/vnd.tapio.contact`.
 * @property sizeBytes exact payload length.
 */
data class ContentHeader(
    val contentKind: ContentKind,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(mimeType.isNotBlank()) { "mimeType must not be blank" }
        require(sizeBytes >= 0L) { "sizeBytes must not be negative" }
    }
}
