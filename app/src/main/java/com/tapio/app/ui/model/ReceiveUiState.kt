package com.tapio.app.ui.model

import androidx.annotation.StringRes
import com.tapio.core.transfer.IncomingFile
import com.tapio.core.transfer.ReceivedFile

/** Everything the receive screen can be showing. */
sealed interface ReceiveUiState {

    /** Listening for a tap. */
    data object WaitingForTap : ReceiveUiState

    /** A tap happened; joining the sender's Wi-Fi Direct group. */
    data object Connecting : ReceiveUiState

    /** Bytes are arriving. [progress] is `0f..1f`. */
    data class Receiving(val progress: Float) : ReceiveUiState

    /** All bytes in; checking the checksum. */
    data object Verifying : ReceiveUiState

    /** File received and verified — show the "Save this file?" dialog. */
    data class Arrived(val incoming: IncomingFile) : ReceiveUiState

    /** User accepted and the file is in the gallery. */
    data class Saved(val file: ReceivedFile) : ReceiveUiState

    /** User declined. */
    data object Declined : ReceiveUiState

    data class Failed(@param:StringRes val messageRes: Int) : ReceiveUiState
}
