package com.tapio.core.nfc.testing

import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.NfcTokenScanner
import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory [NfcTokenAdvertiser] + [NfcTokenScanner] for tests, Compose previews and
 * running the app on a device with no NFC. Drive it from test code with [simulateTap]
 * (or [emitTap] from non-suspending code).
 *
 * Shipped in `main` (not `src/test`) so downstream modules — `core-transfer`, `:app` —
 * can depend on it without a test-fixtures setup.
 *
 * The tap stream keeps a `replay` of 1 so a collector that subscribes just after a
 * tap still sees it — this removes a race in single-device demo flows.
 */
class FakeNfcHandshake(
    var availability: NfcAvailability = NfcAvailability.Ready,
) : NfcTokenAdvertiser, NfcTokenScanner {

    private val taps = MutableSharedFlow<HandshakeOutcome>(replay = 1, extraBufferCapacity = 16)

    /** The last token passed to [advertise], for assertions. */
    var advertisedToken: SessionToken? = null
        private set

    override fun checkAvailability(): NfcAvailability = availability

    override suspend fun advertise(token: SessionToken): Nothing {
        failFastIfUnavailable()
        advertisedToken = token
        awaitCancellation()
    }

    override fun scan(): Flow<HandshakeOutcome> = taps.asSharedFlow()

    /** Emits [outcome] to every active [scan] collector, as if a device had just tapped. */
    suspend fun simulateTap(outcome: HandshakeOutcome) {
        taps.emit(outcome)
    }

    /** Non-suspending [simulateTap] for callers without a coroutine (demo buttons, previews). */
    fun emitTap(outcome: HandshakeOutcome): Boolean = taps.tryEmit(outcome)

    /** Drops any buffered tap so the next [scan] collector starts clean. */
    fun clearTaps() {
        taps.resetReplayCache()
    }

    private fun failFastIfUnavailable() {
        when (availability) {
            NfcAvailability.Disabled -> throw HandshakeError.NfcDisabled
            NfcAvailability.Unsupported -> throw HandshakeError.NfcUnsupported
            NfcAvailability.Ready -> Unit
        }
    }
}
