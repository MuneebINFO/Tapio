package com.tapio.core.nfc.android

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.tapio.core.nfc.apdu.ApduProtocol

/**
 * Host Card Emulation endpoint: makes the sending phone look like an NFC tag that
 * hands out one [com.tapio.core.nfc.domain.SessionToken].
 *
 * The token is staged from application code via [stageToken] (see [HceTokenAdvertiser])
 * and cleared as soon as advertising stops, so the phone never leaks a stale token.
 *
 * Instantiated by the Android NFC stack — see the `<service>` entry in this module's
 * manifest and `res/xml/tapio_apdu_service.xml`.
 */
class TapioHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return ApduProtocol.STATUS_UNSUPPORTED

        return when {
            ApduProtocol.selectedAid(apdu)?.contentEquals(ApduProtocol.TAPIO_AID) == true ->
                ApduProtocol.STATUS_SUCCESS

            ApduProtocol.isReadTokenApdu(apdu) -> {
                val token = staged
                if (token == null) {
                    ApduProtocol.STATUS_NOT_FOUND
                } else {
                    ApduProtocol.withStatus(token)
                }
            }

            else -> ApduProtocol.STATUS_UNSUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) = Unit

    companion object {
        @Volatile
        private var staged: ByteArray? = null

        /** Publishes [tokenBytes] (from `SessionTokenCodec.encode`) to the next reader. */
        fun stageToken(tokenBytes: ByteArray) {
            staged = tokenBytes
        }

        /** Stops advertising; subsequent read attempts get [ApduProtocol.STATUS_NOT_FOUND]. */
        fun clear() {
            staged = null
        }
    }
}
