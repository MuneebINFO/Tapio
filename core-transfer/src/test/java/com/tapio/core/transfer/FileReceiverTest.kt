package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.testing.FakeWifiDirectConnector
import com.tapio.core.transfer.testing.InMemoryFileSink
import com.tapio.core.transfer.testing.InMemoryTransferChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReceiverTest {

    private fun receiver(wire: ByteArray, sink: InMemoryFileSink = InMemoryFileSink()) = FileReceiver(
        connector = FakeWifiDirectConnector(InMemoryTransferChannel(wire)),
        fileSink = sink,
        config = TransferFixtures.config(chunkSizeBytes = 4096),
    )

    @Test
    fun `previews, then on accept stages and verifies the file`() = runTest {
        val payload = ByteArray(20_003) { (it % 200).toByte() }
        val sink = InMemoryFileSink()
        val wire = TransferFixtures.wireBytes("clip.mp4", "video/mp4", payload)

        val states = receiver(wire, sink).receive(TransferFixtures.token()) { true }.toList()

        assertTrue(states.any { it is TransferState.PreviewReady })
        assertTrue(states.any { it is TransferState.InProgress })
        assertTrue(states.contains(TransferState.Verifying))

        val incoming = (states.last() as TransferState.Completed).result as TransferResult.Received
        val file = incoming.content as IncomingContent.File
        assertEquals("clip.mp4", file.header.displayName)

        file.save()
        assertArrayEquals(payload, sink.persisted)
        assertFalse(sink.discarded)
    }

    @Test
    fun `the preview carries the real name and size`() = runTest {
        val wire = TransferFixtures.wireBytes("holiday.jpg", "image/jpeg", ByteArray(4096))

        val preview = receiver(wire).receive(TransferFixtures.token()) { false }
            .toList()
            .filterIsInstance<TransferState.PreviewReady>()
            .single()
            .preview

        assertEquals("holiday.jpg", preview.displayName)
        assertEquals(4096L, preview.sizeBytes)
    }

    @Test
    fun `declining the preview ends at Declined and touches nothing`() = runTest {
        val sink = InMemoryFileSink()
        val wire = TransferFixtures.wireBytes("x.jpg", "image/jpeg", ByteArray(100))

        val states = receiver(wire, sink).receive(TransferFixtures.token()) { false }.toList()

        assertEquals(TransferState.Declined, states.last())
        assertFalse(sink.discarded)
        assertNull(sink.persisted)
    }

    @Test
    fun `a contact card is verified and parsed - file sink untouched`() = runTest {
        val card = TransferFixtures.contactContent("Jean Dupont", "+33 6 11 22 33 44")
        val payload = ContactCardCodec.encode(card)
        val wire = TransferFixtures.wireBytes(
            "Jean Dupont",
            "application/vnd.tapio.contact",
            payload,
            ContentKind.CONTACT,
        )
        val sink = InMemoryFileSink()

        val states = receiver(wire, sink).receive(TransferFixtures.token()) { true }.toList()

        val contact = ((states.last() as TransferState.Completed).result as TransferResult.Received)
            .content as IncomingContent.Contact
        assertEquals("Jean Dupont", contact.card.displayName)
        assertEquals("+33 6 11 22 33 44", contact.card.phoneNumber)
        assertFalse(sink.discarded)
    }

    @Test
    fun `a corrupt checksum fails and discards the staging file`() = runTest {
        val sink = InMemoryFileSink()
        val wire = TransferFixtures.wireBytes("x.jpg", "image/jpeg", "abc".toByteArray(), trailer = ByteArray(32))

        val states = receiver(wire, sink).receive(TransferFixtures.token()) { true }.toList()

        assertTrue((states.last() as TransferState.Failed).error is TransferError.ChecksumMismatch)
        assertTrue(sink.discarded)
    }

    @Test
    fun `a truncated stream fails as MalformedStream and discards the staging file`() = runTest {
        val full = TransferFixtures.wireBytes("x.jpg", "image/jpeg", ByteArray(500))
        val sink = InMemoryFileSink()

        val states = receiver(full.copyOf(full.size - 120), sink)
            .receive(TransferFixtures.token()) { true }
            .toList()

        assertTrue((states.last() as TransferState.Failed).error is TransferError.MalformedStream)
        assertTrue(sink.discarded)
    }

    @Test
    fun `connection failure is surfaced as Failed`() = runTest {
        val states = FileReceiver(
            FakeWifiDirectConnector(failWith = TransferError.ConnectionLost),
            InMemoryFileSink(),
            TransferFixtures.config(),
        ).receive(TransferFixtures.token()) { true }.toList()

        assertEquals(
            listOf(TransferState.Connecting, TransferState.Failed(TransferError.ConnectionLost)),
            states,
        )
    }
}
