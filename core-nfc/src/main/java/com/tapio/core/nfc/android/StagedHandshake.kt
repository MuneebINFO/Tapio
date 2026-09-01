package com.tapio.core.nfc.android

/**
 * The one handshake payload the sending device is currently offering over NFC.
 *
 * Both HCE services read from here: [TapioHostApduService] (custom APDU, used when
 * the other phone is already in Tapio's reader mode) and [NdefHostApduService]
 * (NDEF Type-4 tag emulation, so a tap launches Tapio even when it is closed).
 *
 * Written by [HceTokenAdvertiser] while the "hold the phones together" screen is
 * up, and cleared the moment it leaves — the device never keeps a stale token.
 */
internal object StagedHandshake {

    @Volatile
    var tokenBytes: ByteArray? = null
        private set

    fun stage(bytes: ByteArray) {
        tokenBytes = bytes
    }

    fun clear() {
        tokenBytes = null
    }
}
