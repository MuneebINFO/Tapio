package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.testing.FakeWifiDirectConnector
import com.tapio.core.transfer.testing.InMemoryFileSource
import com.tapio.core.transfer.testing.InMemoryTransferChannel
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import com.tapio.core.transfer.wire.readFully
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSenderTest {

    private val accept = TransferFraming.ACK_BYTE.toByte()
    private val decline = TransferFraming.DECLINE_BYTE.toByte()

    @Test
    fun `happy path emits preview, progress and Completed, and frames the wire`() = runTest {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        val channel = InMemoryTransferChannel(incoming = byteArrayOf(accept, accept))
        val sender = FileSender(
            connector = FakeWifiDirectConnector(channel),
            fileSource = InMemoryFileSource(mapOf("content://pic" to payload)),
            config = TransferFixtures.config(chunkSizeBytes = 4096),
        )

        val states = sender.send(
            TransferFixtures.fileContent("content://pic", payload.size.toLong()),
            TransferFixtures.token(),
        ).toList()

        assertEquals(TransferState.Connecting, states.first())
        assertTrue(states.contains(TransferState.AwaitingPeerDecision))
        assertTrue("expected at least one progress update", states.any { it is TransferState.InProgress })
        assertEquals(TransferState.Completed(TransferResult.Sent(payload.size.toLong())), states.last())

        val wire = channel.writtenBytes().inputStream()
        val preview = TransferFraming.readPreview(wire)
        assertEquals("photo.jpg", preview.displayName)
        assertEquals(payload.size.toLong(), preview.sizeBytes)

        val header = TransferFraming.readHeader(wire)
        assertEquals(payload.size.toLong(), header.sizeBytes)
        assertArrayEquals(payload, wire.readFully(payload.size))
        assertArrayEquals(Sha256.of(payload).bytes, TransferFraming.readChecksumTrailer(wire))
        assertEquals(-1, wire.read())
        assertTrue(channel.closed)
    }

    @Test
    fun `progress ends at 100 percent`() = runTest {
        val payload = ByteArray(9_001)
        val sender = FileSender(
            FakeWifiDirectConnector(InMemoryTransferChannel()),
            InMemoryFileSource(mapOf("u" to payload)),
            TransferFixtures.config(chunkSizeBytes = 1024),
        )

        val content = TransferFixtures.fileContent("u", payload.size.toLong())
        val lastProgress = sender.send(content, TransferFixtures.token())
            .toList()
            .filterIsInstance<TransferState.InProgress>()
            .last()

        assertEquals(payload.size.toLong(), lastProgress.progress.bytesTransferred)
        assertEquals(1f, lastProgress.progress.fraction)
    }

    @Test
    fun `sends a contact card through the same framed channel`() = runTest {
        val channel = InMemoryTransferChannel(incoming = byteArrayOf(accept, accept))
        val sender = FileSender(
            FakeWifiDirectConnector(channel),
            InMemoryFileSource(emptyMap()),
            TransferFixtures.config(),
        )
        val card = TransferFixtures.contactContent("Marie Curie", "0600000000")

        val states = sender.send(card, TransferFixtures.token()).toList()

        assertTrue(states.last() is TransferState.Completed)

        val wire = channel.writtenBytes().inputStream()
        assertEquals(ContentKind.CONTACT, TransferFraming.readPreview(wire).contentKind)
        val header = TransferFraming.readHeader(wire)
        assertEquals("Marie Curie", header.displayName)
        assertEquals(card, ContactCardCodec.decode(wire.readFully(header.sizeBytes.toInt())))
    }

    @Test
    fun `a declined preview ends at Declined and sends nothing`() = runTest {
        val channel = InMemoryTransferChannel(incoming = byteArrayOf(decline))
        val sender = FileSender(
            FakeWifiDirectConnector(channel),
            InMemoryFileSource(mapOf("u" to ByteArray(500))),
            TransferFixtures.config(),
        )

        val states = sender.send(TransferFixtures.fileContent("u", 500), TransferFixtures.token()).toList()

        assertEquals(TransferState.Declined, states.last())
        // only the preview frame was written
        val wire = channel.writtenBytes().inputStream()
        TransferFraming.readPreview(wire)
        assertEquals(-1, wire.read())
    }

    @Test
    fun `a missing receipt ack is surfaced as NotConfirmed`() = runTest {
        val channel = InMemoryTransferChannel(incoming = byteArrayOf(accept)) // accept, then no receipt
        val sender = FileSender(
            FakeWifiDirectConnector(channel),
            InMemoryFileSource(mapOf("u" to ByteArray(200))),
            TransferFixtures.config(),
        )

        val states = sender.send(TransferFixtures.fileContent("u", 200), TransferFixtures.token()).toList()

        assertEquals(TransferState.Failed(TransferError.NotConfirmed), states.last())
    }

    @Test
    fun `connection failure is surfaced as Failed`() = runTest {
        val sender = FileSender(
            FakeWifiDirectConnector(failWith = TransferError.WifiDirectUnavailable),
            InMemoryFileSource(emptyMap()),
            TransferFixtures.config(),
        )

        val states = sender.send(TransferFixtures.fileContent("u", 0), TransferFixtures.token()).toList()

        assertEquals(
            listOf(TransferState.Connecting, TransferState.Failed(TransferError.WifiDirectUnavailable)),
            states,
        )
    }
}
