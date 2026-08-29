package com.tapio.core.nfc

import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.flow.Flow

/**
 * Sender side of the handshake: exposes a locally-minted [SessionToken] to whatever
 * device touches this one next.
 *
 * Split from [NfcTokenScanner] on purpose — a screen is almost always doing exactly
 * one of "I want to send" or "I'm waiting to receive", and the two are backed by
 * completely different Android machinery (Host Card Emulation vs. reader mode).
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

/**
 * Receiver side of the handshake: reads a [SessionToken] from the next device that
 * touches this one.
 */
interface NfcTokenScanner {

    fun checkAvailability(): NfcAvailability

    /**
     * Cold flow that activates NFC scanning on collection and tears it down on
     * cancellation. Emits one [HandshakeOutcome] per tap.
     */
    fun scan(): Flow<HandshakeOutcome>
}
