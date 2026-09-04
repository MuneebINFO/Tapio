package com.tapio.core.nfc.android

import android.content.Context
import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.awaitCancellation

/**
 * [NfcTokenAdvertiser] backed by [TapioHostApduService]. Stage the token while the
 * "hold your phones together" screen is on-screen:
 *
 * ```
 * launch { advertiser.advertise(myToken) }  // cancel the job to stop advertising
 * ```
 */
class HceTokenAdvertiser(context: Context) : NfcTokenAdvertiser {

    private val appContext = context.applicationContext
    private val availabilityChecker = AndroidNfcAvailabilityChecker(appContext)

    override fun checkAvailability(): NfcAvailability = availabilityChecker.current()

    override suspend fun advertise(token: SessionToken): Nothing {
        when (checkAvailability()) {
            NfcAvailability.Disabled -> throw HandshakeError.NfcDisabled
            NfcAvailability.Unsupported -> throw HandshakeError.NfcUnsupported
            NfcAvailability.Ready -> Unit
        }

        try {
            StagedHandshake.stage(SessionTokenCodec.encode(token))
            awaitCancellation()
        } finally {
            StagedHandshake.clear()
        }
    }

    companion object {
        /**
         * True while this device is offering a token over NFC, i.e. it is the *sender*
         * of the tap about to happen.
         *
         * A phone in card-emulation mode still polls for tags, so a sender can discover
         * the other Tapio's (empty) emulated tag and mistake the tap for an incoming
         * transfer. Anything that reacts to a tap must check this first: reader mode and
         * card emulation are mutually exclusive, so reading here would silence the very
         * tag the other phone is trying to read.
         */
        val isAdvertising: Boolean get() = StagedHandshake.tokenBytes != null
    }
}
