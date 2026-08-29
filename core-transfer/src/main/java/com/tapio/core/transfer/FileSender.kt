package com.tapio.core.transfer

import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Sends one file to a peer: connects over Wi-Fi Direct using the NFC handshake
 * token, writes `[header][bytes][SHA-256 trailer]`, and reports progress.
 *
 * ```
 * FileSender(connector, fileSource)
 *     .send(pickedPhoto, sessionToken)
 *     .collect { state -> render(state) }
 * ```
 *
 * All work runs on [TransferConfig.dispatcher]; cancelling the collector aborts
 * the transfer and closes the channel.
 */
class FileSender(
    private val connector: WifiDirectConnector,
    private val fileSource: FileSource,
    private val config: TransferConfig = TransferConfig(),
) {
    fun send(content: SharedContent.File, token: SessionToken): Flow<TransferState> = flow {
        emit(TransferState.Connecting)
        val channel = connectOrFail(connector, token, config) ?: return@flow

        channel.use {
            val totalBytes = fileSource.sizeOf(content)
            val digest = Sha256.newDigest()
            val output = channel.openOutput()

            TransferFraming.writeHeader(output, FileHeader(content.displayName, content.mimeType, totalBytes))

            fileSource.openStream(content).use { source ->
                pumpBytes(source, output, totalBytes, digest, config, bounded = false)
            }

            output.write(digest.digest())
            output.flush()

            emit(TransferState.Completed(TransferResult.Sent(totalBytes)))
        }
    }
        .catch { cause -> emit(TransferState.Failed(cause.asTransferError())) }
        .flowOn(config.dispatcher)
}
