package com.tapio.app.data

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.android.TapioNdef
import com.tapio.core.nfc.domain.SessionToken

/**
 * Pulls a [SessionToken] out of the `ACTION_NDEF_DISCOVERED` intent Android fires
 * when another phone (running Tapio's NDEF tag emulation) is tapped against this
 * one — this is what lets a transfer start even when Tapio was closed.
 *
 * Returns `null` for any other intent.
 */
fun Intent.tapioHandshakeToken(): SessionToken? {
    if (action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null

    val messages = ndefMessages() ?: return null
    val tokenBytes = TapioNdef.tokenBytesFrom(messages) ?: return null
    return runCatching { SessionTokenCodec.decode(tokenBytes) }.getOrNull()
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
