package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.Checksum
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Receives one [SharedContent][com.tapio.core.common.SharedContent] from a peer:
 * connects, reads the header, streams the bytes while hashing them, checks the
 * SHA-256 trailer, then hands back an [IncomingContent].
 *
 * A file lands in a [FileSink] staging area; a contact card is parsed in memory.
 * Either way the flow **stops** at [TransferState.Completed] — nothing is written
 * to the gallery or the address book until the user accepts. Any failure discards
 * the staging file automatically.
 */
class FileReceiver(
    private val connector: WifiDirectConnector,
    private val fileSink: FileSink,
    private val config: TransferConfig = TransferConfig(),
) {
    fun receive(token: SessionToken): Flow<TransferState> = flow {
        emit(TransferState.Connecting)
        val channel = connectOrFail(connector, token, config) ?: return@flow

        channel.use {
            val input = channel.openInput()
            val header = TransferFraming.readHeader(input)
            when (header.contentKind) {
                ContentKind.FILE -> receiveFile(input, header)
                ContentKind.CONTACT -> receiveContact(input, header)
            }
        }
    }
        .catch { cause -> emit(TransferState.Failed(cause.asTransferError())) }
        .flowOn(config.dispatcher)

    private suspend fun FlowCollector<TransferState>.receiveFile(input: InputStream, header: ContentHeader) {
        val staged = fileSink.create(header)
        val digest = Sha256.newDigest()
        var completed = false
        try {
            pumpBytes(input, staged.output, header.sizeBytes, digest, config, bounded = true)
            val verified = verifyTrailer(input, digest)
            staged.close()
            completed = true
            emit(TransferState.Completed(TransferResult.Received(IncomingContent.File(header, verified, staged))))
        } finally {
            if (!completed) runCatching { staged.discard() }
        }
    }

    private suspend fun FlowCollector<TransferState>.receiveContact(input: InputStream, header: ContentHeader) {
        val digest = Sha256.newDigest()
        val buffer = ByteArrayOutputStream()
        pumpBytes(input, buffer, header.sizeBytes, digest, config, bounded = true)
        verifyTrailer(input, digest)

        val card = runCatching { ContactCardCodec.decode(buffer.toByteArray()) }
            .getOrElse { throw TransferError.MalformedStream("invalid contact card") }
        emit(TransferState.Completed(TransferResult.Received(IncomingContent.Contact(header, card))))
    }

    private suspend fun FlowCollector<TransferState>.verifyTrailer(
        input: InputStream,
        digest: java.security.MessageDigest,
    ): Checksum {
        emit(TransferState.Verifying)
        val expected = Checksum(TransferFraming.readChecksumTrailer(input))
        val actual = Checksum(digest.digest())
        if (expected != actual) throw TransferError.ChecksumMismatch(expected.hex, actual.hex)
        return actual
    }
}
