package com.tapio.core.common

/**
 * A single item a user wants to hand to another device by touching phones.
 *
 * The type is a sealed hierarchy on purpose: a new content kind is a new subtype
 * plus a receiver-side renderer, with no change to the NFC handshake or the
 * transfer pipeline — both only care about [kind] and [byteSize].
 */
sealed interface SharedContent {

    /** Stable discriminator, used to pick a receiver-side renderer and for routing. */
    val kind: ContentKind

    /** Best-effort size in bytes, used for progress reporting. `null` when unknown up front. */
    val byteSize: Long?

    /** Short, human-readable summary shown to the receiver before they accept. */
    val summary: String

    /**
     * A photo or video (and, later, any document) identified by a content URI on
     * the sending device.
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
        override val summary: String get() = displayName
    }

    /**
     * A phone number with the name to file it under — the sender's own number, or
     * someone else's. Tiny and structured, but it still travels over the transfer
     * channel (never NFC) so the receiver can accept before receiving.
     *
     * @param displayName the name the number should be saved as, chosen by the sender.
     * @param phoneNumber the number, in whatever format the sender typed it.
     * @param organization optional company/label.
     */
    data class ContactCard(
        val displayName: String,
        val phoneNumber: String,
        val organization: String? = null,
    ) : SharedContent {
        init {
            require(displayName.isNotBlank()) { "contact displayName must not be blank" }
            require(phoneNumber.isNotBlank()) { "contact phoneNumber must not be blank" }
        }

        override val kind: ContentKind = ContentKind.CONTACT
        override val byteSize: Long? = null
        override val summary: String get() = displayName
    }
}

/** Discriminator for [SharedContent] subtypes and for the transfer header. */
enum class ContentKind {
    FILE,
    CONTACT,
}
