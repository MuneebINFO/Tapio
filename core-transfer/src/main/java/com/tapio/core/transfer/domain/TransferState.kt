package com.tapio.core.transfer.domain

import com.tapio.core.transfer.IncomingFile

/**
 * The state machine a transfer moves through, emitted as a `Flow` by
 * [com.tapio.core.transfer.FileSender] and [com.tapio.core.transfer.FileReceiver].
 *
 * The UI maps these one-to-one onto its animated states
 * (waiting → in progress → success / error).
 */
sealed interface TransferState {

    /** Establishing the Wi-Fi Direct connection off the back of the NFC handshake. */
    data object Connecting : TransferState

    /** Bytes are moving; [progress] updates repeatedly. */
    data class InProgress(val progress: TransferProgress) : TransferState

    /** All bytes received; comparing the checksum trailer (receiver only). */
    data object Verifying : TransferState

    /** Terminal success. */
    data class Completed(val result: TransferResult) : TransferState

    /** Terminal failure. */
    data class Failed(val error: TransferError) : TransferState
}

/** The payload of [TransferState.Completed]. */
sealed interface TransferResult {

    /** Sender side: the file left this device. */
    data class Sent(val bytesSent: Long) : TransferResult

    /**
     * Receiver side: the file is fully written to a staging area and its checksum
     * is verified. The user still has to accept it — see [IncomingFile].
     */
    data class Received(val file: IncomingFile) : TransferResult
}
