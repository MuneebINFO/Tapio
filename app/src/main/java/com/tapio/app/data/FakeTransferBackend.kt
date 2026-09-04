package com.tapio.app.data

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.TransferChannel
import com.tapio.core.transfer.TransferConfig
import com.tapio.core.transfer.WifiDirectConnector
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.testing.InMemoryFileSink
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * A [TransferBackend] that runs both ends of a transfer in this process. Real files
 * are read through [fileSource]; the NFC tap and the Wi-Fi Direct link are simulated
 * via [demo], so the whole experience works on a single device.
 */
class FakeTransferBackend(
    private val fileSource: FileSource,
    private val transferConfig: TransferConfig = TransferConfig(),
) : TransferBackend, DemoControls {

    private val sampleImage = ByteArray(SAMPLE_SIZE_BYTES) { ((it * 31) % 256).toByte() }
    private val sampleContact = SharedContent.ContactCard("Léa Martin", "+33 6 98 76 54 32", "Studio Tapio")

    @Volatile
    private var senderChannels = DemoChannels()

    @Volatile
    private var pendingIncoming: DemoChannels? = null

    private val _incomingToken = MutableStateFlow<SessionToken?>(null)

    override val demo: DemoControls get() = this
    override val incomingToken: StateFlow<SessionToken?> = _incomingToken.asStateFlow()

    override val advertiser: NfcTokenAdvertiser = object : NfcTokenAdvertiser {
        override fun checkAvailability() = NfcAvailability.Ready
        override suspend fun advertise(token: SessionToken): Nothing = awaitCancellation()
    }

    override fun newSender(): FileSender {
        senderChannels = DemoChannels()
        return FileSender(senderChannels.outgoingConnector, fileSource, transferConfig)
    }

    override fun newReceiver(): FileReceiver {
        val channels = pendingIncoming ?: DemoChannels()
        pendingIncoming = null
        return FileReceiver(channels.incomingConnector, InMemoryFileSink(), transferConfig)
    }

    override suspend fun createLocalToken(payloadSummary: String): SessionToken =
        fakeToken("Cet appareil", payloadSummary)

    override fun peerPicksUpContent() {
        senderChannels.open()
    }

    override fun simulateIncomingFile() {
        stageIncoming(fileWire("photo-recue.jpg", "image/jpeg", sampleImage))
        _incomingToken.value = fakeToken("Téléphone de test", "Une photo · 768 Ko")
    }

    override fun simulateIncomingContact() {
        stageIncoming(
            wire(
                ContentKind.CONTACT,
                sampleContact.displayName,
                "application/vnd.tapio.contact",
                ContactCardCodec.encode(sampleContact),
            ),
        )
        _incomingToken.value = fakeToken("Téléphone de Léa", "Un contact")
    }

    override fun clearIncoming() {
        _incomingToken.value = null
    }

    private fun stageIncoming(wire: ByteArray) {
        pendingIncoming = DemoChannels().apply {
            incoming = wire
            open()
        }
    }

    private fun fakeToken(deviceName: String, summary: String) = SessionToken(
        sessionId = UUID.randomUUID(),
        wifiSsid = "DIRECT-tapio-demo",
        wifiPassphrase = "demo-passphrase",
        deviceName = deviceName,
        payloadSummary = summary,
        role = HandshakeRole.SENDER,
        issuedAtEpochMs = System.currentTimeMillis(),
    )

    private companion object {
        const val SAMPLE_SIZE_BYTES = 768 * 1024

        fun fileWire(name: String, mime: String, payload: ByteArray) =
            wire(ContentKind.FILE, name, mime, payload)

        fun wire(kind: ContentKind, name: String, mime: String, payload: ByteArray): ByteArray =
            ByteArrayOutputStream().apply {
                val size = payload.size.toLong()
                TransferFraming.writePreview(this, ContentPreview(kind, name, mime, size, null))
                TransferFraming.writeHeader(this, ContentHeader(kind, name, mime, size))
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
            override suspend fun openInput(): InputStream = ByteArrayInputStream(
                byteArrayOf(TransferFraming.ACK_BYTE.toByte(), TransferFraming.ACK_BYTE.toByte()),
            )

            override fun close() = Unit
        }
    }

    val incomingConnector = gatedConnector {
        object : TransferChannel {
            override suspend fun openInput(): InputStream = ByteArrayInputStream(incoming)
            override suspend fun openOutput(): OutputStream = ByteArrayOutputStream()
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
