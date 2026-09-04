package com.tapio.core.transfer.android

import com.tapio.core.transfer.TransferChannel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

private const val SOCKET_READ_TIMEOUT_MS = 60_000

/**
 * [TransferChannel] over a connected TCP [Socket] on the Wi-Fi Direct group.
 *
 * The wrapped streams are created once and reused — a full transfer reads and
 * writes on the same socket (bytes one way, the receipt acknowledgement back), so
 * a second buffered wrapper would swallow bytes.
 */
internal class SocketTransferChannel(private val socket: Socket) : TransferChannel {

    init {
        runCatching {
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            // The handshake frames are tiny; without this Nagle holds them back waiting
            // for more data, and the peer sits idle on a link that may not survive it.
            socket.tcpNoDelay = true
            socket.keepAlive = true
        }
    }

    private val input: InputStream by lazy { BufferedInputStream(socket.getInputStream()) }
    private val output: OutputStream by lazy { BufferedOutputStream(socket.getOutputStream()) }

    override suspend fun openInput(): InputStream = input

    override suspend fun openOutput(): OutputStream = output

    override fun close() {
        runCatching { socket.close() }
    }
}
