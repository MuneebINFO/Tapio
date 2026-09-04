package com.tapio.core.nfc.android

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.tapio.core.nfc.apdu.ApduProtocol

private const val TAG = "TapioHce"

/**
 * Host Card Emulation endpoint: makes the sending phone look like an NFC tag that
 * hands out one [com.tapio.core.nfc.domain.SessionToken] (staged by [HceTokenAdvertiser]).
 *
 * Instantiated by the Android NFC stack — see the `<service>` entry in this module's
 * manifest and `res/xml/tapio_apdu_service.xml`.
 */
class TapioHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return ApduProtocol.STATUS_UNSUPPORTED

        return when {
            ApduProtocol.selectedAid(apdu)?.contentEquals(ApduProtocol.TAPIO_AID) == true -> {
                Log.i(TAG, "AID selected (token staged=${StagedHandshake.tokenBytes != null})")
                ApduProtocol.STATUS_SUCCESS
            }

            ApduProtocol.isReadTokenApdu(apdu) -> {
                val token = StagedHandshake.tokenBytes
                Log.i(TAG, "read-token requested; ${token?.size ?: -1} bytes staged")
                token?.let { ApduProtocol.withStatus(it) } ?: ApduProtocol.STATUS_NOT_FOUND
            }

            else -> ApduProtocol.STATUS_UNSUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "deactivated (reason=$reason)")
    }
}
