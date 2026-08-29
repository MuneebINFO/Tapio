package com.tapio.core.transfer.domain

/**
 * The metadata the sender transmits before the file bytes, so the receiver knows
 * what it is about to receive and can create the right kind of destination.
 *
 * The integrity checksum is **not** here — it travels as a trailer after the bytes
 * so both sides can compute it in a single streaming pass.
 */
data class FileHeader(
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
