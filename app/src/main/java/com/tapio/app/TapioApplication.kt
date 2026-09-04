package com.tapio.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.tapio.app.data.AndroidTransferBackend
import com.tapio.app.data.FakeTransferBackend
import com.tapio.app.data.TransferBackend
import com.tapio.core.transfer.android.ContentResolverFileSource

/**
 * Application entry point and hand-rolled dependency container.
 *
 * [transferBackend] is the real NFC + Wi-Fi Direct backend on a capable device
 * (Android 10+, Wi-Fi Direct, NFC), and the in-process [FakeTransferBackend]
 * otherwise — so the flows stay explorable on an emulator or an older phone.
 */
class TapioApplication : Application() {

    val transferBackend: TransferBackend by lazy { createBackend() }

    private fun createBackend(): TransferBackend {
        val capable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

        return if (capable) {
            AndroidTransferBackend(this)
        } else {
            FakeTransferBackend(fileSource = ContentResolverFileSource(this))
        }
    }
}
