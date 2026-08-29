package com.tapio.core.nfc.android

import android.content.Context
import android.nfc.NfcAdapter
import com.tapio.core.nfc.domain.NfcAvailability

/** Maps the platform [NfcAdapter] state onto Tapio's [NfcAvailability]. */
class AndroidNfcAvailabilityChecker(private val context: Context) {

    fun current(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context.applicationContext)
            ?: return NfcAvailability.Unsupported

        return if (adapter.isEnabled) NfcAvailability.Ready else NfcAvailability.Disabled
    }
}
