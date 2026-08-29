package com.tapio.app

import android.app.Application
import com.tapio.app.data.FakeTransferBackend
import com.tapio.app.data.TransferBackend
import com.tapio.core.transfer.android.ContentResolverFileSource

/**
 * Application entry point and hand-rolled dependency container (no DI framework —
 * see the "keep it light" note in CONTRIBUTING).
 *
 * [transferBackend] is a [FakeTransferBackend] for now: it reads real files but
 * simulates the NFC tap and the Wi-Fi Direct link, so the whole UI is usable on a
 * single device. The real NFC + Wi-Fi Direct backend gets wired here in step 5.
 */
class TapioApplication : Application() {

    val transferBackend: TransferBackend by lazy {
        FakeTransferBackend(fileSource = ContentResolverFileSource(this))
    }
}
