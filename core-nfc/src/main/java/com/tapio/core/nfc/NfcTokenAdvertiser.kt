package com.tapio.core.nfc

import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken

/**
 * Sender side of the handshake: exposes a locally-minted [SessionToken] to whatever
 * device touches this one next.
 *
 * There is no receiver-side counterpart interface. A phone does not *scan* for a tap
 * — it gets tapped: the platform dispatches the sender's emulated tag to an activity,
 * which reads the token with
 * [TapioTagReader][com.tapio.core.nfc.android.TapioTagReader]. Only the sending half
 * is a long-lived, injectable capability worth abstracting.
 */
interface NfcTokenAdvertiser {

    fun checkAvailability(): NfcAvailability

    /**
     * Publishes [token] over NFC and suspends until the caller's scope is cancelled,
     * keeping the payload live for the duration.
     *
     * @throws com.tapio.core.nfc.domain.HandshakeError.NfcDisabled if NFC is off.
     * @throws com.tapio.core.nfc.domain.HandshakeError.NfcUnsupported if there is no NFC hardware.
     */
    suspend fun advertise(token: SessionToken): Nothing
}
