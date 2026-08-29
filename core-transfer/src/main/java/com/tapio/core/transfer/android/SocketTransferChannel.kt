package com.tapio.core.transfer.android

import com.tapio.core.transfer.TransferChannel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/** [TransferChannel] over a connected TCP [Socket] on the Wi-Fi Direct group. */
internal class SocketTransferChannel(private val socket: Socket) : TransferChannel {

    override suspend fun openInput(): InputStream = BufferedInputStream(socket.getInputStream())

    override suspend fun openOutput(): OutputStream = BufferedOutputStream(socket.getOutputStream())

    override fun close() {
        runCatching { socket.close() }
    }
}
