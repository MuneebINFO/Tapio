package com.tapio.app.data

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.nfc.testing.FakeNfcHandshake
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.TransferConfig
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.testing.InMemoryFileSink
import com.tapio.core.transfer.testing.InMemoryFileSource
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
 * are read through [fileSource]; only the NFC tap and the Wi-Fi Direct link are
 * simulated, so the whole send/receive experience — including the accept prompt
 * and contact save — works on a single device.
 */
class FakeTransferBackend(
    private val fileSource: FileSource,
    private val transferConfig: TransferConfig = TransferConfig(),
) : TransferBackend, DemoControls {

    private val nfc = FakeNfcHandshake()

    private val samplePayload = ByteArray(SAMPLE_SIZE_BYTES) { ((it * 31) % 256).toByte() }
    private val sampleFile = SharedContent.File(
        uri = "tapio://demo/incoming",
        displayName = "photo-recue.jpg",
        mimeType = "image/jpeg",
        byteSize = samplePayload.size.toLong(),
    )
    private val sampleContact = SharedContent.ContactCard(
        displayName = "Léa Martin",
        phoneNumber = "+33 6 98 76 54 32",
        organization = "Studio Tapio",
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
        channels = DemoChannels()
        return FileReceiver(channels.incomingConnector, InMemoryFileSink(), transferConfig)
    }

    override suspend fun createLocalToken(payloadSummary: String): SessionToken =
        fakeToken("Cet appareil", payloadSummary)

    override fun peerPicksUpContent() {
        channels.open()
    }

    override fun peerSendsSampleFile() {
        channels.incoming = fileWire(sampleFile.displayName, sampleFile.mimeType, samplePayload)
        channels.open()
        nfc.emitTap(HandshakeOutcome.Success(fakeToken("Téléphone de test", "Une photo · 768 Ko")))
    }

    override fun peerSharesSampleContact() {
        val payload = ContactCardCodec.encode(sampleContact)
        channels.incoming = wire(ContentKind.CONTACT, sampleContact.displayName, MIME_CONTACT, payload)
        channels.open()
        nfc.emitTap(HandshakeOutcome.Success(fakeToken("Téléphone de Léa", "Un contact")))
    }

    private fun fakeToken(deviceName: String, summary: String) = SessionToken(
        sessionId = UUID.randomUUID(),
        wifiDirectMac = FAKE_MAC,
        deviceName = deviceName,
        payloadSummary = summary,
        role = HandshakeRole.SENDER,
        issuedAtEpochMs = System.currentTimeMillis(),
    )

    private companion object {
        const val FAKE_MAC = "02:00:00:00:00:00"
        const val MIME_CONTACT = "application/vnd.tapio.contact"
        const val SAMPLE_SIZE_BYTES = 768 * 1024

        fun fileWire(name: String, mime: String, payload: ByteArray) =
            wire(ContentKind.FILE, name, mime, payload)

        fun wire(kind: ContentKind, name: String, mime: String, payload: ByteArray): ByteArray =
            ByteArrayOutputStream().apply {
                TransferFraming.writeHeader(this, ContentHeader(kind, name, mime, payload.size.toLong()))
                write(payload)
                write(Sha256.of(payload).bytes)
            }.toByteArray()
    }
}

/** One transfer's worth of in-memory plumbing, gated so the UI controls when it "connects". */
private class DemoChannels {

    private val gate = CompletableDeferred<Unit>()

    @Volatile
    var incoming: ByteArray = ByteArray(0)

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
