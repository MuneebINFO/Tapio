package com.tapio.core.transfer

import android.util.Log

/**
 * Traces each frame of a transfer under the `TapioWire` tag, so a failure on real
 * hardware says *which* step broke rather than just "connection lost".
 *
 * Calls are wrapped: on the JVM (unit tests) `android.util.Log` is not stubbed and
 * would throw, and a log line must never be able to fail a transfer.
 */
internal object TransferLog {

    private const val TAG = "TapioWire"

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun w(message: String, cause: Throwable? = null) {
        runCatching { if (cause == null) Log.w(TAG, message) else Log.w(TAG, message, cause) }
    }
}
