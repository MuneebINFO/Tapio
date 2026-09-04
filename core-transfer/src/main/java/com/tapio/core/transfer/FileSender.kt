package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream

/** MIME type carried in the header for a serialised [SharedContent.ContactCard]. */
const val MIME_TAPIO_CONTACT: String = "application/vnd.tapio.contact"

/**
 * Sends one [SharedContent] to a peer.
 *
 * Two phases over one socket:
 * 1. **Preview** — send a thumbnail + real name/size, wait for the other person's
 *    accept/decline.
 * 2. **Content** (only if accepted) — stream `[header][bytes][SHA-256 trailer]`,
 *    then wait for the receipt ack before reporting success.
 *
 * A [SharedContent.File] streams from the [fileSource]; a
 * [SharedContent.ContactCard] is serialised from memory — same frame, same checksum.
 */
class FileSender(
    private val connector: WifiDirectConnector,
    private val fileSource: FileSource,
    private val config: TransferConfig = TransferConfig(),
) {
    fun send(content: SharedContent, token: SessionToken): Flow<TransferState> = flow {
        emit(TransferState.Connecting)
        val channel = connectOrFail(connector, token, config) ?: return@flow

        channel.use {
            val output = channel.openOutput()
            val input = channel.openInput()

            val preview = buildPreview(content)
            TransferLog.i("send: preview ready (${preview.displayName}, thumb=${preview.thumbnailJpeg?.size ?: 0}B)")
            TransferFraming.writePreview(output, preview)
            TransferLog.i("send: preview written, waiting for the decision")
            emit(TransferState.AwaitingPeerDecision)
            if (!TransferFraming.readDecision(input)) {
                TransferLog.i("send: peer declined")
                emit(TransferState.Declined)
                return@use
            }

            val outgoing = describe(content)
            val digest = Sha256.newDigest()

            TransferLog.i("send: accepted — streaming ${outgoing.header.sizeBytes} bytes")
            TransferFraming.writeHeader(output, outgoing.header)
            outgoing.bytes.use { source ->
                pumpBytes(source, output, outgoing.header.sizeBytes, digest, config, bounded = false)
            }
            output.write(digest.digest())
            output.flush()

            TransferLog.i("send: bytes flushed, waiting for the receipt")
            emit(TransferState.Verifying)
            if (!TransferFraming.readReceiptAck(input)) throw TransferError.NotConfirmed

            TransferLog.i("send: receipt confirmed")
            emit(TransferState.Completed(TransferResult.Sent(outgoing.header.sizeBytes)))
        }
    }
        .catch { cause ->
            TransferLog.w("send failed", cause)
            emit(TransferState.Failed(cause.asTransferError()))
        }
        .flowOn(config.dispatcher)

    private suspend fun buildPreview(content: SharedContent): ContentPreview = when (content) {
        is SharedContent.File -> ContentPreview(
            contentKind = ContentKind.FILE,
            displayName = content.displayName,
            mimeType = content.mimeType,
            sizeBytes = fileSource.sizeOf(content),
            thumbnailJpeg = runCatching { fileSource.thumbnail(content) }.getOrNull(),
        )

        is SharedContent.ContactCard -> ContentPreview(
            contentKind = ContentKind.CONTACT,
            displayName = content.displayName,
            mimeType = MIME_TAPIO_CONTACT,
            sizeBytes = 0L,
            thumbnailJpeg = null,
        )
    }

    private class Outgoing(val header: ContentHeader, val bytes: InputStream)

    private suspend fun describe(content: SharedContent): Outgoing = when (content) {
        is SharedContent.File -> {
            val size = fileSource.sizeOf(content)
            Outgoing(
                header = ContentHeader(ContentKind.FILE, content.displayName, content.mimeType, size),
                bytes = fileSource.openStream(content),
            )
        }

        is SharedContent.ContactCard -> {
            val payload = ContactCardCodec.encode(content)
            Outgoing(
                header = ContentHeader(
                    ContentKind.CONTACT,
                    content.displayName,
                    MIME_TAPIO_CONTACT,
                    payload.size.toLong(),
                ),
                bytes = payload.inputStream(),
            )
        }
    }
}
