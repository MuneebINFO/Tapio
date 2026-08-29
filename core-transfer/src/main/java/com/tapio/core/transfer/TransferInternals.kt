package com.tapio.core.transfer

import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferProgress
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Connects via [connector], mapping failures onto an emitted
 * [TransferState.Failed]. Returns `null` (after emitting) when the connection
 * could not be established, so the caller can `return@flow`.
 */
@Suppress("SwallowedException") // the timeout is mapped to a typed error; its stack trace is noise
internal suspend fun FlowCollector<TransferState>.connectOrFail(
    connector: WifiDirectConnector,
    token: SessionToken,
    config: TransferConfig,
): TransferChannel? =
    try {
        withTimeout(config.connectTimeoutMs) { connector.connect(token) }
    } catch (timeout: TimeoutCancellationException) {
        emit(TransferState.Failed(TransferError.ConnectionTimedOut))
        null
    } catch (error: TransferError) {
        emit(TransferState.Failed(error))
        null
    }

/**
 * Streams bytes from [source] to [destination], updating [digest] and emitting
 * throttled [TransferState.InProgress] snapshots against [totalBytes].
 *
 * @param bounded when `true`, reads exactly [totalBytes] and fails on early EOF
 *   (receiver side); when `false`, reads to EOF (sender side, reading a local file).
 */
@Suppress("LongParameterList")
internal suspend fun FlowCollector<TransferState>.pumpBytes(
    source: InputStream,
    destination: OutputStream,
    totalBytes: Long,
    digest: MessageDigest,
    config: TransferConfig,
    bounded: Boolean,
) {
    val buffer = ByteArray(config.chunkSizeBytes)
    var transferred = 0L
    var lastEmittedAt = 0L

    while (!bounded || transferred < totalBytes) {
        val capacity = if (bounded) {
            minOf(buffer.size.toLong(), totalBytes - transferred).toInt()
        } else {
            buffer.size
        }

        val read = source.read(buffer, 0, capacity)
        if (read < 0) {
            if (bounded) {
                throw TransferError.MalformedStream("stream ended ${totalBytes - transferred} bytes early")
            }
            break
        }

        destination.write(buffer, 0, read)
        digest.update(buffer, 0, read)
        transferred += read

        val now = config.clock()
        if (transferred == totalBytes || now - lastEmittedAt >= config.progressIntervalMs) {
            emit(TransferState.InProgress(TransferProgress(transferred, totalBytes)))
            lastEmittedAt = now
        }
    }
    destination.flush()
}

/** Normalises any throwable that escapes the transfer pipeline into a [TransferError]. */
internal fun Throwable.asTransferError(): TransferError = when (this) {
    is TransferError -> this
    is IOException -> TransferError.ConnectionLost
    else -> TransferError.Io(this)
}
