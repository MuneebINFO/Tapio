package com.tapio.app.data

import android.content.Context
import android.telephony.TelephonyManager

/**
 * This SIM's own phone number, if the platform will give it to us without a
 * runtime permission dance. Often `null` (carriers rarely populate it) — the
 * contact form falls back to manual entry.
 */
fun Context.ownPhoneNumberOrNull(): String? = runCatching {
    @Suppress("DEPRECATION", "MissingPermission")
    getSystemService(TelephonyManager::class.java)?.line1Number?.takeIf { it.isNotBlank() }
}.getOrNull()
