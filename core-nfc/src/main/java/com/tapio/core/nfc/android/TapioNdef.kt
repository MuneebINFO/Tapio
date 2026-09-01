package com.tapio.core.nfc.android

import android.nfc.NdefMessage
import android.nfc.NdefRecord

/**
 * The NDEF wrapping used so a tap **launches Tapio on the receiver even when the
 * app is closed**: the sender emulates an NDEF Type-4 tag (see [NdefHostApduService])
 * carrying one MIME record, and Tapio registers an `NDEF_DISCOVERED` intent filter
 * for that MIME type.
 *
 * An Android Application Record is appended so the tap targets Tapio specifically
 * (and offers the Play Store if it is somehow not installed).
 */
object TapioNdef {

    /** MIME type of the handshake record; matched by the app's `<intent-filter>`. */
    const val MIME_TYPE: String = "application/vnd.tapio.handshake"

    /** Wraps the encoded [tokenBytes] into an NDEF message for [appPackage]. */
    fun message(tokenBytes: ByteArray, appPackage: String): NdefMessage = NdefMessage(
        arrayOf(
            NdefRecord.createMime(MIME_TYPE, tokenBytes),
            NdefRecord.createApplicationRecord(appPackage),
        ),
    )

    /**
     * Extracts the encoded session-token bytes from the NDEF messages of an
     * `ACTION_NDEF_DISCOVERED` intent, or `null` if none is a Tapio handshake.
     */
    fun tokenBytesFrom(ndefMessages: List<NdefMessage>): ByteArray? =
        ndefMessages.asSequence()
            .flatMap { it.records.asSequence() }
            .firstOrNull { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, Charsets.US_ASCII).equals(MIME_TYPE, ignoreCase = true)
            }
            ?.payload
}
