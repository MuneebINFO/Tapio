package com.tapio.core.transfer.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.TransferError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real [WifiDirectConnector]: forms a Wi-Fi Direct group with the peer whose MAC
 * arrived in the NFC [SessionToken], then opens a TCP socket over it. The group
 * owner listens; the client dials in.
 *
 * NOTE: needs on-device validation. `WifiP2pConfig.deviceAddress` is deprecated on
 * Android 10+ but remains the only way to target a specific peer discovered
 * out-of-band (here, via NFC); the modern `WifiP2pConfig.Builder` only supports
 * connecting by SSID/BSSID after an in-band scan.
 */
class WifiP2pConnector(
    context: Context,
    private val transferPort: Int = DEFAULT_TRANSFER_PORT,
) : WifiDirectConnector {

    private val appContext = context.applicationContext

    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    override suspend fun connect(token: SessionToken): TransferChannel {
        val wifiP2pManager = manager ?: throw TransferError.WifiDirectUnavailable
        val channel = wifiP2pManager.initialize(appContext, appContext.mainLooper, null)
            ?: throw TransferError.WifiDirectUnavailable

        val info = awaitGroupFormed(wifiP2pManager, channel, token)

        val socket = if (info.isGroupOwner) {
            acceptOn(transferPort)
        } else {
            dial(requireNotNull(info.groupOwnerAddress?.hostAddress) { "missing group owner address" })
        }
        return SocketTransferChannel(socket)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitGroupFormed(
        wifiP2pManager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        token: SessionToken,
    ): WifiP2pInfo = suspendCancellableCoroutine { continuation ->
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        var receiver: BroadcastReceiver? = null

        fun cleanup() {
            receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
            receiver = null
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                wifiP2pManager.requestConnectionInfo(channel) { connectionInfo ->
                    if (connectionInfo.groupFormed && continuation.isActive) {
                        cleanup()
                        continuation.resume(connectionInfo)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        continuation.invokeOnCancellation { cleanup() }

        val config = WifiP2pConfig().apply { deviceAddress = token.wifiDirectMac }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                if (continuation.isActive) {
                    cleanup()
                    continuation.resumeWithException(mapConnectFailure(reason))
                }
            }
        })
    }

    private fun mapConnectFailure(reason: Int): TransferError = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> TransferError.WifiDirectUnavailable
        else -> TransferError.ConnectionLost
    }

    private suspend fun acceptOn(port: Int): Socket = withContext(Dispatchers.IO) {
        ServerSocket(port).use { server ->
            server.soTimeout = SOCKET_TIMEOUT_MS
            server.accept()
        }
    }

    private suspend fun dial(host: String): Socket = withContext(Dispatchers.IO) {
        Socket().apply { connect(InetSocketAddress(host, transferPort), SOCKET_TIMEOUT_MS) }
    }

    private companion object {
        const val DEFAULT_TRANSFER_PORT = 8988
        const val SOCKET_TIMEOUT_MS = 15_000
    }
}
