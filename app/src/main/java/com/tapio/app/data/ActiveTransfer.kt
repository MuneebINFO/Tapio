package com.tapio.app.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.UUID

private const val TAG = "TapioReceive"

/** `android.util.Log` is not stubbed on the JVM, and a log line must never fail a claim. */
private fun log(message: String) {
    runCatching { Log.i(TAG, message) }
}

/**
 * Lets this phone handle **one transfer at a time**.
 *
 * Two situations need this. One physical tap can be delivered twice — Tapio's own
 * reader mode may read the tag while the platform separately dispatches
 * `TECH_DISCOVERED` for it — and each delivery would start its own transfer, the
 * second `WifiP2pManager.connect` failing with `BUSY` and taking both down. And while
 * a transfer is running the phone is committed to one Wi-Fi Direct group, so a second
 * phone tapping (or the same one tapping again) has nothing to join.
 *
 * The claim is held for the length of one transfer and released when its popup goes
 * away. [STALE_AFTER_MS] is only a crash net: the object lives in the app process, so
 * a killed process clears it anyway.
 */
internal object ActiveTransfer {

    private const val STALE_AFTER_MS = 5 * 60_000L

    private var sessionId: UUID? = null
    private var claimedAt = 0L

    /** Returns true if the caller may proceed; false while another transfer holds the phone. */
    @Synchronized
    fun claim(id: UUID): Boolean {
        val held = sessionId
        if (held != null && System.currentTimeMillis() - claimedAt < STALE_AFTER_MS) {
            val what = if (held == id) "duplicate tap" else "another phone"
            log("refusing $what — session $held is still running")
            return false
        }
        if (held != null) log("session $held went stale — taking over")
        sessionId = id
        claimedAt = System.currentTimeMillis()
        return true
    }

    @Synchronized
    fun release(id: UUID) {
        if (sessionId == id) {
            sessionId = null
            log("session $id released")
        }
    }

    @VisibleForTesting
    @Synchronized
    fun reset() {
        sessionId = null
    }
}
