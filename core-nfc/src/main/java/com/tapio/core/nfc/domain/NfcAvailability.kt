package com.tapio.core.nfc.domain

/**
 * Whether this device can take part in an NFC handshake right now.
 *
 * Kept separate from [HandshakeError] so the UI can react *before* the user tries
 * to share (e.g. show a "Turn on NFC" nudge on the home screen).
 */
sealed interface NfcAvailability {

    /** NFC hardware is present and switched on. */
    data object Ready : NfcAvailability

    /** NFC hardware is present but the user has it turned off in system settings. */
    data object Disabled : NfcAvailability

    /** This device has no NFC hardware; the app must fall back to another pairing method. */
    data object Unsupported : NfcAvailability
}
