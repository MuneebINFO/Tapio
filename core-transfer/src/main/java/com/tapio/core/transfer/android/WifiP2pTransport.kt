package com.tapio.core.transfer.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.TransferError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Wi-Fi Direct transport.
 *
 * Wi-Fi Direct is a **peer-to-peer** link the two phones form directly — no router,
 * no internet, no shared network (Wi-Fi just has to be switched on). The sender's
 * phone becomes the group owner (`DIRECT-…`) at the fixed address `192.168.49.1`;
 * the receiver joins it with the SSID + passphrase from the NFC token.
 *
 * The link is torn down as soon as the transfer's receipt is confirmed. Each
 * transfer needs a fresh tap.
 *
 * `adb logcat -s $TAG` shows what the framework is doing when a connection fails.
 */
private const val TAG = "TapioWifiP2p"
private const val TRANSFER_PORT = 8988
private const val GROUP_OWNER_IP = "192.168.49.1"
private const val ACCEPT_TIMEOUT_MS = 45_000
private const val DIAL_ATTEMPTS = 10
private const val DIAL_INTERVAL_MS = 500L
private const val DIAL_SOCKET_TIMEOUT_MS = 2_000
private const val JOIN_TIMEOUT_MS = 20_000L
private const val JOIN_ATTEMPTS = 3
private const val ACTION_TIMEOUT_MS = 6_000L
private const val GROUP_SETTLE_MS = 800L
private const val BACKLOG = 4

/**
 * `groupFormed` fires the moment the link is up, but the group owner is still
 * finishing DHCP and route setup. A socket opened inside that window connects and
 * then dies a fraction of a second later, so we let the link settle first.
 */
private const val LINK_SETTLE_MS = 1_500L

/** The Wi-Fi Direct group credentials the sender puts into the NFC token. */
data class GroupCredentials(val networkName: String, val passphrase: String)

/** Holds one [WifiP2pManager.Channel] for the object's lifetime; re-inits after a disconnect. */
@RequiresApi(Build.VERSION_CODES.Q)
private class P2p(context: Context) {

    val appContext: Context = context.applicationContext
    val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    @Volatile
    private var channel: WifiP2pManager.Channel? = null

    fun require(): Pair<WifiP2pManager, WifiP2pManager.Channel> {
        val mgr = manager ?: throw TransferError.WifiDirectUnavailable
        val ch = channel ?: mgr.initialize(appContext, appContext.mainLooper) {
            Log.w(TAG, "channel disconnected")
            channel = null
        } ?: throw TransferError.WifiDirectUnavailable
        channel = ch
        return mgr to ch
    }

    fun wifiEnabled(): Boolean =
        (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

    @SuppressLint("MissingPermission")
    suspend fun removeGroup() {
        val mgr = manager ?: return
        val ch = channel ?: return
        runCatching { actionListenerResult("removeGroup") { listener -> mgr.removeGroup(ch, listener) } }
    }

    /**
     * Drops whatever Wi-Fi Direct state this phone is still holding — a group from a
     * transfer that was killed rather than finished, or a half-open connect attempt.
     * Left in place, the framework answers the next `connect()` with `BUSY`.
     */
    @SuppressLint("MissingPermission")
    suspend fun clearP2pState() {
        val mgr = manager ?: return
        val ch = channel ?: return
        runCatching { actionListenerResult("cancelConnect") { listener -> mgr.cancelConnect(ch, listener) } }
        removeGroup()
        delay(GROUP_SETTLE_MS)
    }
}

/** Sender side: owns the group; [connector] accepts the single client that joins. */
@RequiresApi(Build.VERSION_CODES.Q)
class WifiP2pHost(context: Context) {

    private val p2p = P2p(context)

    @SuppressLint("MissingPermission")
    suspend fun openGroup(): GroupCredentials {
        val (mgr, ch) = p2p.require()

        p2p.clearP2pState()

        // Pick our own credentials and create a NON-persistent group: no persistent
        // groups piling up between transfers, and no need to poll requestGroupInfo.
        // Some devices let the framework bring the Wi-Fi radio up for P2P even with
        // the toggle "off", so we try first and only blame Wi-Fi if it actually fails.
        val credentials = GroupCredentials(networkName = randomNetworkName(), passphrase = randomPassphrase())
        val config = WifiP2pConfig.Builder()
            .setNetworkName(credentials.networkName)
            .setPassphrase(credentials.passphrase)
            .enablePersistentMode(false)
            .build()

        if (!createGroupWithRetry(mgr, ch, config)) {
            Log.w(TAG, "createGroup gave up (wifiEnabled=${p2p.wifiEnabled()})")
            throw if (p2p.wifiEnabled()) TransferError.ConnectionLost else TransferError.WifiOff
        }

        Log.i(TAG, "group ready: ${credentials.networkName}")
        return credentials
    }

    fun connector(): WifiDirectConnector = object : WifiDirectConnector {
        override suspend fun connect(token: SessionToken): TransferChannel = withContext(Dispatchers.IO) {
            // The listening socket stays open for the whole transfer. Closing it right
            // after accept() resets any connection the kernel had already completed into
            // the backlog — which is exactly what killed the receiver mid-handshake.
            val server = ServerSocket()
            // accept() blocks in a way coroutine cancellation cannot interrupt. Closing
            // the server socket does interrupt it — without this, a cancelled connect
            // leaves a thread that later accepts a live socket and drops it on the
            // floor, which the receiver sees as an abrupt connection abort.
            val onCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { runCatching { server.close() } }
            val socket = try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(TRANSFER_PORT), BACKLOG)
                server.soTimeout = ACCEPT_TIMEOUT_MS
                Log.i(TAG, "waiting for the receiver to connect…")
                server.accept().also {
                    // We may have been cancelled while blocked; never hand back a socket
                    // nobody will read, or the peer waits on a dead connection.
                    if (!isActive) {
                        runCatching { it.close() }
                        runCatching { server.close() }
                        ensureActive()
                    }
                }
            } catch (cancelled: CancellationException) {
                runCatching { server.close() }
                throw cancelled
            } catch (io: IOException) {
                Log.w(TAG, "accept failed: $io")
                runCatching { server.close() }
                throw io.asWifiError()
            } finally {
                onCancel?.dispose()
            }
            Log.i(TAG, "receiver connected from ${socket.inetAddress?.hostAddress}")
            TeardownChannel(SocketTransferChannel(socket)) { runCatching { server.close() } }
        }
    }

    /** Called once the receipt is confirmed (or the screen leaves): the group goes away. */
    suspend fun closeGroup() {
        p2p.removeGroup()
    }

    @SuppressLint("MissingPermission")
    private suspend fun createGroupWithRetry(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        config: WifiP2pConfig,
    ): Boolean {
        repeat(3) { attempt ->
            if (actionListenerResult("createGroup") { listener -> mgr.createGroup(ch, config, listener) }) return true
            Log.w(TAG, "createGroup attempt ${attempt + 1} failed; cleaning up")
            p2p.removeGroup()
            delay(GROUP_SETTLE_MS)
        }
        return false
    }

    private companion object {
        private const val SSID_TAG_LEN = 2
        private const val PASSPHRASE_LEN = 12
        private val ALPHANUM = ('A'..'Z') + ('a'..'z') + ('0'..'9')

        fun randomNetworkName(): String =
            "DIRECT-" + (1..SSID_TAG_LEN).map { ALPHANUM.random() }.joinToString("") + "-Tapio"

        fun randomPassphrase(): String =
            (1..PASSPHRASE_LEN).map { ALPHANUM.random() }.joinToString("")
    }
}

/** Receiver side: joins the sender's group, dials it, and leaves when the channel closes. */
@RequiresApi(Build.VERSION_CODES.Q)
class WifiP2pClient(context: Context) {

    private val p2p = P2p(context)

    fun connector(): WifiDirectConnector = object : WifiDirectConnector {
        override suspend fun connect(token: SessionToken): TransferChannel {
            val (mgr, ch) = p2p.require()

            joinWithRetry(mgr, ch, token)
            delay(LINK_SETTLE_MS)
            val socket = dialGroupOwner()
            return TeardownChannel(SocketTransferChannel(socket)) {
                runCatching { mgr.removeGroup(ch, null) }
            }
        }
    }

    /**
     * Joining fails with `BUSY` whenever the framework still holds state from an earlier
     * transfer — very common, since the popup can be dismissed mid-transfer. Each retry
     * clears that state first, which is what actually makes the second attempt work.
     */
    private suspend fun joinWithRetry(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, token: SessionToken) {
        repeat(JOIN_ATTEMPTS) { attempt ->
            if (attempt > 0) Log.i(TAG, "join attempt ${attempt + 1}")
            p2p.clearP2pState()
            if (joinGroup(mgr, ch, token)) return
        }
        throw if (p2p.wifiEnabled()) TransferError.ConnectionTimedOut else TransferError.WifiOff
    }

    @SuppressLint("MissingPermission")
    private suspend fun joinGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        token: SessionToken,
    ): Boolean {
        val config = WifiP2pConfig.Builder()
            .setNetworkName(token.wifiSsid)
            .setPassphrase(token.wifiPassphrase)
            .build()

        val joined = withTimeoutOrNull(JOIN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var receiver: BroadcastReceiver? = null
                fun cleanup() {
                    receiver?.let { r -> runCatching { p2p.appContext.unregisterReceiver(r) } }
                    receiver = null
                }
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        mgr.requestConnectionInfo(ch) { info ->
                            if (info.groupFormed && cont.isActive) {
                                Log.i(TAG, "joined group (owner=${info.isGroupOwner})")
                                cleanup()
                                cont.resume(true)
                            }
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    p2p.appContext,
                    receiver,
                    IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                cont.invokeOnCancellation { cleanup() }

                Log.i(TAG, "connecting to ${token.wifiSsid}…")
                mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = Unit
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "connect failed: reason=$reason")
                        if (cont.isActive) {
                            cleanup()
                            cont.resume(false)
                        }
                    }
                })
            }
        }

        return joined == true
    }

    private suspend fun dialGroupOwner(): Socket = withContext(Dispatchers.IO) {
        val target = InetSocketAddress(GROUP_OWNER_IP, TRANSFER_PORT)
        repeat(DIAL_ATTEMPTS) { attempt ->
            val socket = runCatching { Socket().apply { connect(target, DIAL_SOCKET_TIMEOUT_MS) } }.getOrNull()
            if (socket != null) {
                Log.i(TAG, "socket to group owner open (attempt ${attempt + 1})")
                return@withContext socket
            }
            if (attempt < DIAL_ATTEMPTS - 1) delay(DIAL_INTERVAL_MS)
        }
        Log.w(TAG, "could not reach the group owner")
        throw TransferError.ConnectionLost
    }
}

/** Wraps a channel so closing it also tears down the Wi-Fi Direct group. */
private class TeardownChannel(
    private val delegate: TransferChannel,
    private val onClose: () -> Unit,
) : TransferChannel {
    override suspend fun openInput(): InputStream = delegate.openInput()
    override suspend fun openOutput(): OutputStream = delegate.openOutput()
    override fun close() {
        runCatching { delegate.close() }
        runCatching { onClose() }
    }
}

/**
 * Awaits a [WifiP2pManager.ActionListener] callback.
 *
 * The framework does not always answer — a wedged supplicant simply never calls back.
 * Without [ACTION_TIMEOUT_MS] a tear-down would hang forever, and since tear-down runs
 * uncancellable so the group is really released, that would strand the whole session.
 */
private suspend fun actionListenerResult(
    tag: String,
    action: (WifiP2pManager.ActionListener) -> Unit,
): Boolean = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
    suspendCancellableCoroutine { cont ->
        action(object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (cont.isActive) cont.resume(true)
            }

            // reason: 0 ERROR · 1 P2P_UNSUPPORTED · 2 BUSY · 3 NO_SERVICE_REQUESTS
            override fun onFailure(reason: Int) {
                Log.w(TAG, "$tag failed: reason=$reason")
                if (cont.isActive) cont.resume(false)
            }
        })
    }
} ?: false.also { Log.w(TAG, "$tag got no answer in $ACTION_TIMEOUT_MS ms") }

private fun Throwable.asWifiError(): TransferError = when (this) {
    is TransferError -> this
    is IOException -> TransferError.ConnectionLost
    else -> TransferError.Io(this)
}
