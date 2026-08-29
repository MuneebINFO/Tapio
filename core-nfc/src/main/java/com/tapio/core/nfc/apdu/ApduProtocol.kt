package com.tapio.core.nfc.apdu

/**
 * The tiny ISO 7816-4 APDU dialect Tapio's Host Card Emulation service speaks.
 *
 * Flow, once the phones touch:
 * 1. Receiver (reader mode) sends [buildSelectApdu] to select Tapio's [TAPIO_AID].
 * 2. Sender's HCE service replies [STATUS_SUCCESS].
 * 3. Receiver sends [buildReadTokenApdu].
 * 4. Sender replies with `encode(token) + STATUS_SUCCESS` (see [withStatus]).
 *
 * All functions here are pure and framework-free so they can be unit-tested on the JVM.
 */
object ApduProtocol {

    /** Proprietary application id: `F0 'T' 'A' 'P' 'I' 'O' 01`. */
    val TAPIO_AID: ByteArray = byteArrayOf(0xF0.toByte(), 0x54, 0x41, 0x50, 0x49, 0x4F, 0x01)

    /** SW1/SW2 `90 00` — command completed successfully. */
    val STATUS_SUCCESS: ByteArray = byteArrayOf(0x90.toByte(), 0x00)

    /** SW1/SW2 `6A 82` — the requested data is not available (no token staged yet). */
    val STATUS_NOT_FOUND: ByteArray = byteArrayOf(0x6A, 0x82.toByte())

    /** SW1/SW2 `6D 00` — the instruction byte is not supported. */
    val STATUS_UNSUPPORTED: ByteArray = byteArrayOf(0x6D, 0x00)

    private const val CLA_ISO = 0x00
    private const val INS_SELECT = 0xA4
    private const val P1_SELECT_BY_NAME = 0x04

    /** Proprietary "read the staged token" instruction (READ BINARY-style). */
    const val INS_READ_TOKEN = 0xB0

    private const val HEADER_SIZE = 5
    private const val STATUS_WORD_SIZE = 2

    /** Builds a SELECT-by-name APDU for [aid]. */
    fun buildSelectApdu(aid: ByteArray = TAPIO_AID): ByteArray =
        byteArrayOf(
            CLA_ISO.toByte(),
            INS_SELECT.toByte(),
            P1_SELECT_BY_NAME.toByte(),
            0x00,
            aid.size.toByte(),
        ) + aid + byteArrayOf(0x00)

    /** True if [apdu] is a SELECT-by-name command. */
    fun isSelectApdu(apdu: ByteArray): Boolean =
        apdu.size >= HEADER_SIZE &&
            apdu[0].toInt() and 0xFF == CLA_ISO &&
            apdu[1].toInt() and 0xFF == INS_SELECT

    /** Extracts the AID from a SELECT command, or `null` if [apdu] is not a well-formed SELECT. */
    fun selectedAid(apdu: ByteArray): ByteArray? {
        if (!isSelectApdu(apdu)) return null
        val lc = apdu[4].toInt() and 0xFF
        return if (apdu.size < HEADER_SIZE + lc) null else apdu.copyOfRange(HEADER_SIZE, HEADER_SIZE + lc)
    }

    /** Builds the "give me the staged token" command. */
    fun buildReadTokenApdu(): ByteArray =
        byteArrayOf(CLA_ISO.toByte(), INS_READ_TOKEN.toByte(), 0x00, 0x00, 0x00)

    /** True if [apdu] is a read-token command. */
    fun isReadTokenApdu(apdu: ByteArray): Boolean =
        apdu.size >= 4 && apdu[1].toInt() and 0xFF == INS_READ_TOKEN

    /** Appends a 2-byte status word to [payload]. */
    fun withStatus(payload: ByteArray, status: ByteArray = STATUS_SUCCESS): ByteArray =
        payload + status

    /**
     * Splits an APDU response into its payload and trailing status word.
     *
     * @throws IllegalArgumentException if [response] is shorter than a status word.
     */
    fun parseResponse(response: ByteArray): ApduResponse {
        require(response.size >= STATUS_WORD_SIZE) {
            "APDU response must be at least $STATUS_WORD_SIZE bytes"
        }
        val split = response.size - STATUS_WORD_SIZE
        return ApduResponse(
            payload = response.copyOfRange(0, split),
            status = response.copyOfRange(split, response.size),
        )
    }
}

/** An APDU response decomposed into its data [payload] and 2-byte [status] word. */
class ApduResponse(val payload: ByteArray, val status: ByteArray) {

    val isSuccess: Boolean get() = status.contentEquals(ApduProtocol.STATUS_SUCCESS)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApduResponse) return false
        return payload.contentEquals(other.payload) && status.contentEquals(other.status)
    }

    override fun hashCode(): Int = 31 * payload.contentHashCode() + status.contentHashCode()

    override fun toString(): String =
        "ApduResponse(payload=${payload.size} bytes, status=${status.joinToString("") { "%02X".format(it) }})"
}
