package com.tapio.app.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The three moments Tapio wants the user to *feel*: the phones making contact, a
 * transfer completing, and a failure. Distinct patterns so they are recognisable
 * without looking at the screen.
 */
class TapioHaptics(private val vibrator: Vibrator?) {

    /** A short, firm tick when the NFC contact is detected. */
    fun contact() = vibrate(longArrayOf(0, 18, 40, 24))

    /** An upbeat double-pulse when the file has fully transferred. */
    fun success() = vibrate(longArrayOf(0, 24, 60, 44))

    /** A heavier buzz on error. */
    fun error() = vibrate(longArrayOf(0, 120))

    private fun vibrate(pattern: LongArray) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

@Composable
fun rememberTapioHaptics(): TapioHaptics {
    val context = LocalContext.current
    return remember { TapioHaptics(systemVibrator(context)) }
}

private fun systemVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
