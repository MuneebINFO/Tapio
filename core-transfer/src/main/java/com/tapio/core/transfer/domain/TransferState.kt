package com.tapio.core.transfer.domain

import com.tapio.core.transfer.IncomingContent

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

    /** Receiver: connected, the [preview] is in — waiting for the user to accept or decline. */
    data class PreviewReady(val preview: ContentPreview) : TransferState

    /** Sender: preview sent, waiting for the other person's decision. */
    data object AwaitingPeerDecision : TransferState

    /** Terminal: the preview was declined; no bytes were transferred. */
    data object Declined : TransferState

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

    /** Sender side: the payload left this device. */
    data class Sent(val bytesSent: Long) : TransferResult

    /**
     * Receiver side: the payload is fully received and its checksum verified. The
     * user still has to accept it — see [IncomingContent].
     */
    data class Received(val content: IncomingContent) : TransferResult
}
