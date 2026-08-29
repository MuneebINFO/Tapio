package com.tapio.core.transfer.domain

/**
 * A content hash (SHA-256 in practice) with value semantics, used to verify that
 * a received file is byte-for-byte identical to what was sent.
 */
class Checksum(bytes: ByteArray) {

    /** Defensive copy so the digest cannot be mutated after construction. */
    val bytes: ByteArray = bytes.copyOf()

    val hex: String by lazy(LazyThreadSafetyMode.NONE) {
        this.bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Checksum && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "Checksum($hex)"

    companion object {
        /** Parses a lowercase/uppercase hex string such as `"a1b2…"`. */
        fun ofHex(hex: String): Checksum {
            require(hex.length % 2 == 0) { "hex string must have an even length" }
            return Checksum(
                ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                },
            )
        }
    }
}
