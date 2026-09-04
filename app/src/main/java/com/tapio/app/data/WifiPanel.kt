package com.tapio.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Opens the quick Wi-Fi toggle — a slide-up panel on Android 10+, full settings below. */
object WifiPanel {

    fun open(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
    }
}
