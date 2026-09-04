package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.Checksum
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Receives one [SharedContent][com.tapio.core.common.SharedContent] from a peer.
 *
 * The flow connects, reads the [preview][com.tapio.core.transfer.domain.ContentPreview],
 * emits [TransferState.PreviewReady] and then **waits** — the UI shows the preview
 * and calls [respondToPreview]. On accept it streams and verifies the content and
 * hands back an [IncomingContent]; nothing is written to the gallery or address
 * book until the user accepts that too. On decline the sender is told and the link
 * is dropped.
 */
class FileReceiver(
    private val connector: WifiDirectConnector,
    private val fileSink: FileSink,
    private val config: TransferConfig = TransferConfig(),
) {
    /**
     * @param decide called once with the [ContentPreview]; suspend it until the
     *   user taps accept/decline, then return their choice.
     */
    fun receive(
        token: SessionToken,
        decide: suspend (ContentPreview) -> Boolean,
    ): Flow<TransferState> = flow {
        emit(TransferState.Connecting)
        val channel = connectOrFail(connector, token, config) ?: return@flow

        channel.use {
            val input = channel.openInput()
            val output = channel.openOutput()

            TransferLog.i("recv: waiting for the preview")
            val preview = TransferFraming.readPreview(input)
            TransferLog.i("recv: preview ${preview.displayName} (${preview.sizeBytes}B)")
            emit(TransferState.PreviewReady(preview))

            val accepted = decide(preview)
            TransferLog.i("recv: user ${if (accepted) "accepted" else "declined"}")
            TransferFraming.writeDecision(output, accepted)
            if (!accepted) {
                emit(TransferState.Declined)
                return@use
            }

            val header = TransferFraming.readHeader(input)
            TransferLog.i("recv: header ${header.contentKind} ${header.sizeBytes}B")
            when (header.contentKind) {
                ContentKind.FILE -> receiveFile(input, output, header)
                ContentKind.CONTACT -> receiveContact(input, output, header)
            }
        }
    }
        .catch { cause ->
            TransferLog.w("recv failed", cause)
            emit(TransferState.Failed(cause.asTransferError()))
        }
        .flowOn(config.dispatcher)

    private suspend fun FlowCollector<TransferState>.receiveFile(
        input: InputStream,
        ackOut: OutputStream,
        header: ContentHeader,
    ) {
        val staged = fileSink.create(header)
        val digest = Sha256.newDigest()
        var completed = false
        try {
            pumpBytes(input, staged.output, header.sizeBytes, digest, config, bounded = true)
            val verified = verifyTrailer(input, digest)
            staged.close()
            // The file is already verified; a failed ack only means the sender will
            // report "not confirmed", never that this side loses the file.
            runCatching { TransferFraming.writeReceiptAck(ackOut) }
            completed = true
            emit(TransferState.Completed(TransferResult.Received(IncomingContent.File(header, verified, staged))))
        } finally {
            if (!completed) runCatching { staged.discard() }
        }
    }

    private suspend fun FlowCollector<TransferState>.receiveContact(
        input: InputStream,
        ackOut: OutputStream,
        header: ContentHeader,
    ) {
        val digest = Sha256.newDigest()
        val buffer = ByteArrayOutputStream()
        pumpBytes(input, buffer, header.sizeBytes, digest, config, bounded = true)
        verifyTrailer(input, digest)

        val card = runCatching { ContactCardCodec.decode(buffer.toByteArray()) }
            .getOrElse { throw TransferError.MalformedStream("invalid contact card") }
        runCatching { TransferFraming.writeReceiptAck(ackOut) }
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
