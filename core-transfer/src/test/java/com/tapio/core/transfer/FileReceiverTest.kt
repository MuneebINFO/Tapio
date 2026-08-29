package com.tapio.core.transfer

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
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReceiverTest {

    @Test
    fun `happy path stages the file, verifies it, and save persists exact bytes`() = runTest {
        val payload = ByteArray(20_003) { (it % 200).toByte() }
        val sink = InMemoryFileSink()
        val receiver = FileReceiver(
            connector = FakeWifiDirectConnector(
                InMemoryTransferChannel(TransferFixtures.wireBytes("clip.mp4", "video/mp4", payload)),
            ),
            fileSink = sink,
            config = TransferFixtures.config(chunkSizeBytes = 4096),
        )

        val states = receiver.receive(TransferFixtures.token()).toList()

        assertEquals(TransferState.Connecting, states.first())
        assertTrue(states.any { it is TransferState.InProgress })
        assertTrue(states.contains(TransferState.Verifying))

        val completed = states.last() as TransferState.Completed
        val incoming = (completed.result as TransferResult.Received).file
        assertEquals("clip.mp4", incoming.header.displayName)
        assertEquals(payload.size.toLong(), incoming.header.sizeBytes)

        incoming.save()
        assertArrayEquals(payload, sink.persisted)
        assertFalse(sink.discarded)
    }

    @Test
    fun `a corrupt checksum trailer fails and discards the staging file`() = runTest {
        val payload = "the quick brown fox".toByteArray()
        val sink = InMemoryFileSink()
        val wire = TransferFixtures.wireBytes("x.jpg", "image/jpeg", payload, trailer = ByteArray(32))

        val states = FileReceiver(
            FakeWifiDirectConnector(InMemoryTransferChannel(wire)),
            sink,
            TransferFixtures.config(),
        ).receive(TransferFixtures.token()).toList()

        assertTrue((states.last() as TransferState.Failed).error is TransferError.ChecksumMismatch)
        assertTrue(sink.discarded)
        assertEquals(null, sink.persisted)
    }

    @Test
    fun `a truncated stream fails as MalformedStream and discards the staging file`() = runTest {
        val payload = ByteArray(500)
        val full = TransferFixtures.wireBytes("x.jpg", "image/jpeg", payload)
        val sink = InMemoryFileSink()

        val states = FileReceiver(
            FakeWifiDirectConnector(InMemoryTransferChannel(full.copyOf(full.size - 120))),
            sink,
            TransferFixtures.config(),
        ).receive(TransferFixtures.token()).toList()

        assertTrue((states.last() as TransferState.Failed).error is TransferError.MalformedStream)
        assertTrue(sink.discarded)
    }

    @Test
    fun `connection failure is surfaced as Failed`() = runTest {
        val states = FileReceiver(
            FakeWifiDirectConnector(failWith = TransferError.ConnectionLost),
            InMemoryFileSink(),
            TransferFixtures.config(),
        ).receive(TransferFixtures.token()).toList()

        assertEquals(
            listOf(TransferState.Connecting, TransferState.Failed(TransferError.ConnectionLost)),
            states,
        )
    }
}
