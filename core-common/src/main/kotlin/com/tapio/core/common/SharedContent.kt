package com.tapio.core.common

/**
 * A single item a user wants to hand to another device by touching phones.
 *
 * Tapio ships with [File] support first, but the type is a sealed hierarchy on
 * purpose: adding `Text`, `Link` or `Contact` later is a new subtype plus a new
 * renderer, with no change to the NFC handshake or the transfer pipeline, both of
 * which only care about [kind] and [byteSize].
 */
sealed interface SharedContent {

    /** Stable identifier for routing/telemetry and for picking a receiver-side renderer. */
    val kind: ContentKind

    /** Best-effort size in bytes, used for progress reporting. `null` when unknown up front. */
    val byteSize: Long?

    /**
     * A photo or video (and, later, any arbitrary document) identified by a
     * content URI on the sending device.
     *
     * @param uri an opaque `content://` URI owned by the sender; never transmitted as-is.
     * @param displayName file name shown to the receiver in the "Save this file?" dialog.
     * @param mimeType e.g. `image/jpeg`, `video/mp4`.
     */
    data class File(
        val uri: String,
        val displayName: String,
        val mimeType: String,
        override val byteSize: Long?,
    ) : SharedContent {
        override val kind: ContentKind = ContentKind.FILE
    }
}

/** Discriminator for [SharedContent] subtypes. */
enum class ContentKind {
    FILE,

    // Reserved for upcoming content types — see the roadmap in the README.
    // TEXT,
    // LINK,
    // CONTACT,
}
