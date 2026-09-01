package com.tapio.core.common

import java.util.Base64

/**
 * Serialises a [SharedContent.ContactCard] to/from the bytes that travel over the
 * transfer channel. Same compact, dependency-free style as the NFC and file-header
 * codecs: one UTF-8 line, pipe-separated, each field Base64 so it can hold any
 * character.
 *
 * ```
 * TCARD1|<base64(displayName)>|<base64(phoneNumber)>|<base64(organization)>
 * ```
 */
object ContactCardCodec {

    private const val MAGIC = "TCARD1"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 4

    fun encode(card: SharedContent.ContactCard): ByteArray = listOf(
        MAGIC,
        encodeField(card.displayName),
        encodeField(card.phoneNumber),
        encodeField(card.organization.orEmpty()),
    ).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)

    /** @throws IllegalArgumentException if [bytes] are not a valid contact card. */
    fun decode(bytes: ByteArray): SharedContent.ContactCard {
        val fields = bytes.toString(Charsets.UTF_8).trim().split(SEPARATOR)
        require(fields.size == FIELD_COUNT && fields[0] == MAGIC) { "malformed contact card" }

        val displayName = decodeField(fields[1])
        val phoneNumber = decodeField(fields[2])
        val organization = decodeField(fields[3]).ifBlank { null }

        return SharedContent.ContactCard(displayName, phoneNumber, organization)
    }

    private fun encodeField(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeField(value: String): String =
        runCatching { String(Base64.getDecoder().decode(value), Charsets.UTF_8) }
            .getOrElse { throw IllegalArgumentException("contact field is not valid Base64") }
}
