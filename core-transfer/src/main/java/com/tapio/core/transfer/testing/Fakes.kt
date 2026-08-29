package com.tapio.core.transfer.testing

import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileSink
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.ReceivedFile
import com.tapio.core.transfer.StagedFile
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferError
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * In-memory implementations of the `core-transfer` ports for unit tests, Compose
 * previews, and running the app without real Wi-Fi Direct hardware.
 *
 * Shipped in `main` (not `src/test`) so `:app` and future modules can reuse them.
 */

/** A [TransferChannel] whose output is captured and whose input replays fixed bytes. */
class InMemoryTransferChannel(
    private val incoming: ByteArray = ByteArray(0),
) : TransferChannel {

    val sent = ByteArrayOutputStream()
    var closed: Boolean = false
        private set

    /** The full byte stream written by the sender side. */
    fun writtenBytes(): ByteArray = sent.toByteArray()

    override suspend fun openInput(): InputStream = ByteArrayInputStream(incoming)
    override suspend fun openOutput(): OutputStream = sent
    override fun close() {
        closed = true
    }
}

/** A [WifiDirectConnector] that hands back a fixed channel, or fails on demand. */
class FakeWifiDirectConnector(
    private val channel: TransferChannel = InMemoryTransferChannel(),
    private val failWith: TransferError? = null,
    private val delayMs: Long = 0L,
) : WifiDirectConnector {

    var connectCount: Int = 0
        private set

    override suspend fun connect(token: SessionToken): TransferChannel {
        connectCount++
        if (delayMs > 0L) delay(delayMs)
        failWith?.let { throw it }
        return channel
    }
}

/** A [FileSource] backed by a map of `uri -> bytes`. */
class InMemoryFileSource(private val filesByUri: Map<String, ByteArray>) : FileSource {

    override suspend fun sizeOf(content: SharedContent.File): Long =
        bytesFor(content).size.toLong()

    override suspend fun openStream(content: SharedContent.File): InputStream =
        ByteArrayInputStream(bytesFor(content))

    private fun bytesFor(content: SharedContent.File): ByteArray =
        filesByUri[content.uri] ?: throw TransferError.Io(IllegalStateException("no fake file at ${content.uri}"))
}

/** A [FileSink] that stages received bytes in memory and records the user's choice. */
class InMemoryFileSink : FileSink {

    /** Non-null once a staged file has been persisted (user tapped "Save"). */
    var persisted: ByteArray? = null
        private set

    /** True once a staged file has been discarded. */
    var discarded: Boolean = false
        private set

    lateinit var lastHeader: FileHeader
        private set

    override suspend fun create(header: FileHeader): StagedFile {
        lastHeader = header
        return InMemoryStagedFile()
    }

    private inner class InMemoryStagedFile : StagedFile {
        private val buffer = ByteArrayOutputStream()
        override val output: OutputStream get() = buffer

        override fun close() = buffer.close()

        override suspend fun persist(): ReceivedFile {
            persisted = buffer.toByteArray()
            val name = lastHeader.displayName
            return ReceivedFile(uri = "memory://received/$name", displayName = name)
        }

        override suspend fun discard() {
            discarded = true
        }
    }
}
