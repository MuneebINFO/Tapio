package com.tapio.app.ui.model

import androidx.annotation.StringRes
import com.tapio.core.transfer.IncomingContent

/** Everything the receive screen can be showing. */
sealed interface ReceiveUiState {

    /** Listening for a tap. */
    data object WaitingForTap : ReceiveUiState

    /**
     * A tap happened (or the app was launched by one). The "special notification":
     * the user must accept before anything is received.
     */
    data class AwaitingAcceptance(val deviceName: String, val summary: String) : ReceiveUiState

    /** Accepted; joining the sender's Wi-Fi Direct group. */
    data object Connecting : ReceiveUiState

    /** Bytes are arriving. [progress] is `0f..1f`. */
    data class Receiving(val progress: Float) : ReceiveUiState

    /** All bytes in; checking the checksum. */
    data object Verifying : ReceiveUiState

    /** A file arrived and verified — show the "Save this file?" dialog. */
    data class FileArrived(val file: IncomingContent.File) : ReceiveUiState

    /** A contact arrived — show the "Save this contact?" dialog. */
    data class ContactArrived(val contact: IncomingContent.Contact) : ReceiveUiState

    /** The item was accepted and handed to the gallery / address book. */
    data class Saved(@param:StringRes val messageRes: Int) : ReceiveUiState

    /** User declined. */
    data object Declined : ReceiveUiState

    data class Failed(@param:StringRes val messageRes: Int) : ReceiveUiState
}
