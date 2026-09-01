package com.tapio.core.nfc.android

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Emulates an **NDEF Type-4 tag** so that touching this phone launches Tapio on
 * the other one — even if Tapio is closed there — via Android's built-in NDEF
 * dispatch. The tag content is [TapioNdef.message] built from the currently
 * [StagedHandshake].
 *
 * Implements the minimal NFC Forum Type-4 read flow: SELECT NDEF application,
 * SELECT + READ the Capability Container, SELECT + READ the NDEF file. Write is
 * not supported.
 *
 * NOTE: needs on-device validation. Some devices reserve the Type-4 AID for the
 * platform; where HCE of `D2760000850101` is not permitted, the in-app reader-mode
 * path ([ReaderModeTokenScanner]) still works.
 */
class NdefHostApduService : HostApduService() {

    private var selected: Selected = Selected.NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_ERROR

        return when {
            apdu.matches(SELECT_NDEF_APP) -> {
                selected = Selected.NONE
                SW_OK
            }

            apdu.isSelectFile(FILE_ID_CC) -> {
                selected = Selected.CC
                SW_OK
            }

            apdu.isSelectFile(FILE_ID_NDEF) -> {
                selected = Selected.NDEF
                SW_OK
            }

            apdu.isReadBinary() -> readBinary(apdu)

            else -> SW_ERROR
        }
    }

    override fun onDeactivated(reason: Int) {
        selected = Selected.NONE
    }

    private fun readBinary(apdu: ByteArray): ByteArray {
        val file = when (selected) {
            Selected.CC -> CAPABILITY_CONTAINER
            Selected.NDEF -> ndefFile()
            Selected.NONE -> ByteArray(0)
        }
        val offset = (apdu[2].toInt() and 0xFF shl 8) or (apdu[3].toInt() and 0xFF)
        val length = apdu[4].toInt() and 0xFF

        return if (offset + length > file.size) {
            SW_ERROR
        } else {
            file.copyOfRange(offset, offset + length) + SW_OK
        }
    }

    /** `[NLEN hi][NLEN lo][NDEF message bytes]` — an empty message when nothing is staged. */
    private fun ndefFile(): ByteArray {
        val message = StagedHandshake.tokenBytes
            ?.let { TapioNdef.message(it, packageName).toByteArray() }
            ?: ByteArray(0)
        return byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message
    }

    private enum class Selected { NONE, CC, NDEF }

    private companion object {
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_ERROR = byteArrayOf(0x6A, 0x82.toByte())

        // SELECT by name, NDEF Tag Application: 00 A4 04 00 07 D2760000850101 00
        val SELECT_NDEF_APP = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
            0x00,
        )

        val FILE_ID_CC = byteArrayOf(0xE1.toByte(), 0x03)
        val FILE_ID_NDEF = byteArrayOf(0xE1.toByte(), 0x04)

        // Capability Container: v2.0, 32 KB NDEF file, read-only.
        val CAPABILITY_CONTAINER = byteArrayOf(
            0x00, 0x0F,             // CCLEN = 15
            0x20,                   // mapping version 2.0
            0x00, 0x3B,             // MLe
            0x00, 0x34,             // MLc
            0x04, 0x06,             // NDEF File Control TLV: T=04, L=06
            0xE1.toByte(), 0x04,    // file id
            0x80.toByte(), 0x00,    // max NDEF file size = 32768
            0x00,                   // read access: granted
            0xFF.toByte(),          // write access: denied
        )

        fun ByteArray.matches(prefix: ByteArray): Boolean =
            size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

        fun ByteArray.isSelectFile(fileId: ByteArray): Boolean =
            size >= 7 &&
                this[0].toInt() and 0xFF == 0x00 &&
                this[1].toInt() and 0xFF == 0xA4 &&
                this[2].toInt() and 0xFF == 0x00 &&
                this[5] == fileId[0] && this[6] == fileId[1]

        fun ByteArray.isReadBinary(): Boolean =
            size >= 5 && this[0].toInt() and 0xFF == 0x00 && this[1].toInt() and 0xFF == 0xB0
    }
}
