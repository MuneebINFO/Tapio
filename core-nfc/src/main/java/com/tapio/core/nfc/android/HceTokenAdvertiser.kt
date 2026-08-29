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
            TapioHostApduService.stageToken(SessionTokenCodec.encode(token))
            awaitCancellation()
        } finally {
            TapioHostApduService.clear()
        }
    }
}
