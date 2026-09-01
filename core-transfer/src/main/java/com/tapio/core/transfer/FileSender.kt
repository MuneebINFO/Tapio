package com.tapio.core.transfer

import com.tapio.core.common.ContactCardCodec
import com.tapio.core.common.ContentKind
import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.ContentHeader
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
 * Sends one [SharedContent] to a peer: connects over Wi-Fi Direct using the NFC
 * handshake token, writes `[header][bytes][SHA-256 trailer]`, and reports progress.
 *
 * A [SharedContent.File] streams from the [fileSource]; a
 * [SharedContent.ContactCard] is serialised and streamed from memory — same frame,
 * same checksum, so the receiver treats them uniformly.
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
            val outgoing = describe(content)
            val digest = Sha256.newDigest()
            val output = channel.openOutput()

            TransferFraming.writeHeader(output, outgoing.header)
            outgoing.bytes.use { source ->
                pumpBytes(source, output, outgoing.header.sizeBytes, digest, config, bounded = false)
            }
            output.write(digest.digest())
            output.flush()

            emit(TransferState.Completed(TransferResult.Sent(outgoing.header.sizeBytes)))
        }
    }
        .catch { cause -> emit(TransferState.Failed(cause.asTransferError())) }
        .flowOn(config.dispatcher)

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
