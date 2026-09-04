package com.tapio.app.data

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.android.TapioNdef
import com.tapio.core.nfc.android.TapioTagReader
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.SessionToken

/** True for an NFC intent that could carry a Tapio handshake (NDEF or ISO-DEP tech). */
fun Intent.isNfcHandshake(): Boolean =
    action == NfcAdapter.ACTION_NDEF_DISCOVERED || action == NfcAdapter.ACTION_TECH_DISCOVERED

/**
 * Pulls a [SessionToken] out of an NFC intent Android fires when another phone
 * (running Tapio) is tapped against this one — what lets a transfer start even
 * when Tapio was closed.
 *
 * `NDEF_DISCOVERED` carries the token directly. `TECH_DISCOVERED` carries only the
 * [Tag]; **this blocks** on the APDU exchange, so call it off the main thread.
 *
 * Returns `null` for any other intent, or when no Tapio token is present.
 */
fun Intent.tapioHandshakeToken(): SessionToken? = when (action) {
    NfcAdapter.ACTION_NDEF_DISCOVERED -> {
        Log.i("TapioReceive", "NDEF_DISCOVERED intent")
        val messages = ndefMessages() ?: return null
        val tokenBytes = TapioNdef.tokenBytesFrom(messages) ?: return null
        runCatching { SessionTokenCodec.decode(tokenBytes) }.getOrNull()
    }

    NfcAdapter.ACTION_TECH_DISCOVERED -> {
        Log.i("TapioReceive", "TECH_DISCOVERED intent")
        val tag = IntentCompat.getParcelableExtra(this, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
        (TapioTagReader.read(tag) as? HandshakeOutcome.Success)?.token
    }

    else -> {
        Log.i("TapioReceive", "non-NFC intent: $action")
        null
    }
}

private fun Intent.ndefMessages(): List<NdefMessage>? {
    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
    }
    return raw?.filterIsInstance<NdefMessage>()?.takeIf { it.isNotEmpty() }
}
