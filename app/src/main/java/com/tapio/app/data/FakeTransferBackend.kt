package com.tapio.app.data

import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.nfc.testing.FakeNfcHandshake
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender
import com.tapio.core.transfer.FileSink
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.TransferConfig
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.testing.InMemoryFileSink
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * A [TransferBackend] that runs both ends of a transfer in this process. Real files
 * are still read through [fileSource]; only the NFC tap and the Wi-Fi Direct link
 * are simulated, so the full send/receive experience works on a single device.
 *
 * @param fileSource reads outgoing files (a real `ContentResolverFileSource` in the app).
 * @param newFileSink where incoming demo files are written (in-memory by default so
 *   the device gallery is not polluted with placeholder bytes).
 */
class FakeTransferBackend(
    private val fileSource: FileSource,
    private val transferConfig: TransferConfig = TransferConfig(),
    private val newFileSink: () -> FileSink = { InMemoryFileSink() },
) : TransferBackend, DemoControls {

    private val nfc = FakeNfcHandshake()

    private val samplePayload = ByteArray(SAMPLE_SIZE_BYTES) { ((it * 31) % 256).toByte() }

    /** The file the "incoming" demo pretends a peer is sending. */
    val sampleIncomingFile = SharedContent.File(
        uri = "tapio://demo/incoming",
        displayName = "photo-recue.jpg",
        mimeType = "image/jpeg",
        byteSize = samplePayload.size.toLong(),
    )

    @Volatile
    private var channels = DemoChannels()

    override val advertiser get() = nfc
    override val scanner get() = nfc
    override val demo: DemoControls get() = this

    override fun newSender(): FileSender {
        channels = DemoChannels()
        return FileSender(channels.outgoingConnector, fileSource, transferConfig)
    }

    override fun newReceiver(): FileReceiver {
        nfc.clearTaps()
        channels = DemoChannels(incoming = wireBytesFor(sampleIncomingFile, samplePayload))
        return FileReceiver(channels.incomingConnector, newFileSink(), transferConfig)
    }

    override suspend fun createLocalToken(): SessionToken = fakeToken("Cet appareil")

    override fun peerPicksUpFile() {
        channels.open()
    }

    override fun peerSendsSampleFile() {
        channels.open()
        nfc.emitTap(HandshakeOutcome.Success(fakeToken("Téléphone de test")))
    }

    private fun fakeToken(deviceName: String) = SessionToken(
        sessionId = UUID.randomUUID(),
        wifiDirectMac = FAKE_MAC,
        deviceName = deviceName,
        role = HandshakeRole.SENDER,
        issuedAtEpochMs = System.currentTimeMillis(),
    )

    private companion object {
        const val FAKE_MAC = "02:00:00:00:00:00"
        const val SAMPLE_SIZE_BYTES = 768 * 1024

        fun wireBytesFor(file: SharedContent.File, payload: ByteArray): ByteArray =
            ByteArrayOutputStream().apply {
                TransferFraming.writeHeader(this, FileHeader(file.displayName, file.mimeType, payload.size.toLong()))
                write(payload)
                write(Sha256.of(payload).bytes)
            }.toByteArray()
    }
}

/** One transfer's worth of in-memory plumbing, gated so the UI controls when it "connects". */
private class DemoChannels(private val incoming: ByteArray = ByteArray(0)) {

    private val gate = CompletableDeferred<Unit>()

    fun open() {
        gate.complete(Unit)
    }

    val outgoingConnector = gatedConnector {
        object : TransferChannel {
            override suspend fun openOutput(): OutputStream = ByteArrayOutputStream()
            override suspend fun openInput(): InputStream = error("outgoing channel has no input")
            override fun close() = Unit
        }
    }

    val incomingConnector = gatedConnector {
        object : TransferChannel {
            override suspend fun openInput(): InputStream = ByteArrayInputStream(incoming)
            override suspend fun openOutput(): OutputStream = error("incoming channel has no output")
            override fun close() = Unit
        }
    }

    private fun gatedConnector(make: () -> TransferChannel) = object : WifiDirectConnector {
        override suspend fun connect(token: SessionToken): TransferChannel {
            gate.await()
            return make()
        }
    }
}
