package com.tapio.core.transfer

import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.Checksum
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Receives one file from a peer: connects, reads the header, streams the bytes
 * into a [FileSink] staging area while hashing them, then compares the SHA-256
 * trailer.
 *
 * On success the flow ends with [TransferState.Completed] wrapping an
 * [IncomingFile] — the bytes are on disk but **not** yet in the user's gallery.
 * The UI shows its "Save this file?" dialog and calls [IncomingFile.save] or
 * [IncomingFile.discard]. Any failure discards the staging file automatically.
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
            val staged = fileSink.create(header)
            val digest = Sha256.newDigest()
            var completed = false

            try {
                pumpBytes(input, staged.output, header.sizeBytes, digest, config, bounded = true)

                emit(TransferState.Verifying)
                val expected = Checksum(TransferFraming.readChecksumTrailer(input))
                val actual = Checksum(digest.digest())
                if (expected != actual) {
                    throw TransferError.ChecksumMismatch(expected.hex, actual.hex)
                }

                staged.close()
                completed = true
                emit(TransferState.Completed(TransferResult.Received(IncomingFile(header, actual, staged))))
            } finally {
                if (!completed) runCatching { staged.discard() }
            }
        }
    }
        .catch { cause -> emit(TransferState.Failed(cause.asTransferError())) }
        .flowOn(config.dispatcher)
}
