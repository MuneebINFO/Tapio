package com.tapio.app.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.android.HceTokenAdvertiser
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender
import com.tapio.core.transfer.TransferConfig
import com.tapio.core.transfer.android.ContentResolverFileSource
import com.tapio.core.transfer.android.MediaStoreFileSink
import com.tapio.core.transfer.android.WifiP2pClient
import com.tapio.core.transfer.android.WifiP2pHost
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The real backend: NFC handshake over Host Card Emulation, content over a Wi-Fi
 * Direct group the sender owns.
 *
 * The sender creates its group in [createLocalToken] so the token can carry the
 * SSID + passphrase; [advertiser] holds that group alive while "hold the phones
 * together" shows and tears it down when the screen leaves. The receiver never
 * scans — the sender's emulated NFC tag launches Tapio through the manifest.
 *
 * Requires Android 10 (Wi-Fi Direct connect-by-credentials); [TapioApplication]
 * falls back to the in-process backend below that.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AndroidTransferBackend(context: Context) : TransferBackend {

    private val appContext = context.applicationContext
    private val host = WifiP2pHost(appContext)
    private val client = WifiP2pClient(appContext)
    private val hce = HceTokenAdvertiser(appContext)

    // Advertising only stages the NFC token. Tearing the group down belongs to
    // [endSession]: the group has to outlive the advertisement, since we stop offering
    // the token as soon as a peer connects but still need the link to send over.
    override val advertiser: NfcTokenAdvertiser = hce

    override fun newSender(): FileSender =
        FileSender(host.connector(), ContentResolverFileSource(appContext), TransferConfig())

    override fun newReceiver(): FileReceiver =
        FileReceiver(client.connector(), MediaStoreFileSink(appContext), TransferConfig())

    override suspend fun createLocalToken(payloadSummary: String): SessionToken {
        val group = host.openGroup()
        return SessionToken(
            sessionId = UUID.randomUUID(),
            wifiSsid = group.networkName,
            wifiPassphrase = group.passphrase,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            payloadSummary = payloadSummary,
            role = HandshakeRole.SENDER,
            issuedAtEpochMs = System.currentTimeMillis(),
        )
    }

    override suspend fun endSession() {
        withContext(NonCancellable) { host.closeGroup() }
    }

    override val demo: DemoControls? = null
}
