package com.tapio.core.nfc

import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.NfcAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Thin orchestration layer the UI/ViewModel talks to instead of an [NfcTokenScanner]
 * directly. It short-circuits with a typed [HandshakeError] when NFC is unavailable,
 * so screens never have to special-case availability before subscribing.
 */
class HandshakeCoordinator(private val scanner: NfcTokenScanner) {

    /**
     * Emits handshake outcomes as the user taps devices. If NFC is off or missing,
     * emits a single [HandshakeOutcome.Failure] and completes.
     */
    fun awaitPeerToken(): Flow<HandshakeOutcome> = flow {
        when (scanner.checkAvailability()) {
            NfcAvailability.Disabled ->
                emit(HandshakeOutcome.Failure(HandshakeError.NfcDisabled))

            NfcAvailability.Unsupported ->
                emit(HandshakeOutcome.Failure(HandshakeError.NfcUnsupported))

            NfcAvailability.Ready ->
                emitAll(scanner.scan())
        }
    }
}
