package com.tapio.core.transfer

import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.testing.FakeWifiDirectConnector
import com.tapio.core.transfer.testing.InMemoryFileSink
import com.tapio.core.transfer.testing.InMemoryFileSource
import com.tapio.core.transfer.testing.InMemoryTransferChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end: whatever [FileSender] writes, [FileReceiver] reconstructs byte-for-byte. */
class TransferRoundTripTest {

    @Test
    fun `sender output feeds receiver input and the file arrives intact`() = runTest {
        val original = ByteArray(50_000) { (it * 7 % 256).toByte() }
        val senderChannel = InMemoryTransferChannel()
        val content = TransferFixtures.fileContent(
            uri = "content://src",
            size = original.size.toLong(),
            name = "video.mp4",
            mime = "video/mp4",
        )

        FileSender(
            FakeWifiDirectConnector(senderChannel),
            InMemoryFileSource(mapOf("content://src" to original)),
            TransferFixtures.config(chunkSizeBytes = 8192),
        ).send(content, TransferFixtures.token()).collect()

        val sink = InMemoryFileSink()
        val states = FileReceiver(
            FakeWifiDirectConnector(InMemoryTransferChannel(senderChannel.writtenBytes())),
            sink,
            TransferFixtures.config(chunkSizeBytes = 8192),
        ).receive(TransferFixtures.token()).toList()

        val incoming = (states.last() as TransferState.Completed).result as TransferResult.Received
        val file = incoming.content as IncomingContent.File
        assertEquals("video.mp4", file.header.displayName)

        file.save()
        assertArrayEquals(original, sink.persisted)
    }

    @Test
    fun `sender and receiver round-trip a contact card`() = runTest {
        val card = TransferFixtures.contactContent(name = "Ada Lovelace", number = "+44 20 7946 0000")
        val senderChannel = InMemoryTransferChannel()

        FileSender(
            FakeWifiDirectConnector(senderChannel),
            InMemoryFileSource(emptyMap()),
            TransferFixtures.config(),
        ).send(card, TransferFixtures.token()).collect()

        val states = FileReceiver(
            FakeWifiDirectConnector(InMemoryTransferChannel(senderChannel.writtenBytes())),
            InMemoryFileSink(),
            TransferFixtures.config(),
        ).receive(TransferFixtures.token()).toList()

        val received = (states.last() as TransferState.Completed).result as TransferResult.Received
        assertEquals(card, (received.content as IncomingContent.Contact).card)
    }
}
