package com.tapio.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Tapio needs for a transfer, resolved for the running
 * Android version. Wi-Fi Direct needs `NEARBY_WIFI_DEVICES` from Android 13, and
 * `ACCESS_FINE_LOCATION` before that (the platform used location as a proxy for
 * nearby-radio access).
 *
 * Nothing else: NFC needs no runtime grant, and the incoming-transfer popup is an
 * activity, not a notification.
 */
object TransferPermissions {

    val required: List<String> = listOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        },
    )

    fun allGranted(context: Context): Boolean =
        required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
