package com.tapio.app.ui.prompt

import androidx.annotation.StringRes
import com.tapio.core.transfer.IncomingContent

/** What the incoming-transfer popup is showing. */
sealed interface PromptUiState {

    /**
     * The tap woke us but the handshake did not complete — the phones parted before
     * the token could be read. The popup stays up with its own NFC reader running, so
     * touching again is caught in-process instead of relaunching everything.
     */
    data object WaitingForTap : PromptUiState

    /** Joining the sender's Wi-Fi Direct group and fetching the preview. */
    data object Connecting : PromptUiState

    /** The question: accept this or not? */
    data class Asking(val deviceName: String, val preview: PromptPreview) : PromptUiState

    data class Receiving(val progress: Float) : PromptUiState

    data object Verifying : PromptUiState

    /** Received + verified, waiting for "save it?". */
    data class FileArrived(val file: IncomingContent.File) : PromptUiState

    data class ContactArrived(val contact: IncomingContent.Contact) : PromptUiState

    /** Terminal, the popup closes itself shortly after. */
    data class Done(@param:StringRes val messageRes: Int) : PromptUiState

    data class Failed(@param:StringRes val messageRes: Int, val enableWifi: Boolean = false) : PromptUiState
}

/** The pre-accept preview: real name + size, and a thumbnail for photos/videos. */
class PromptPreview(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnailJpeg: ByteArray?,
) {
    val isContact: Boolean get() = mimeType == "application/vnd.tapio.contact"
}
