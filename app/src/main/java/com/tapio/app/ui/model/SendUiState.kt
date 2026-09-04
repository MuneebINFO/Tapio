package com.tapio.app.ui.model

import androidx.annotation.StringRes
import com.tapio.core.common.SharedContent

/** Everything the send screen can be showing, in the order it usually happens. */
sealed interface SendUiState {

    /** Pick what to share: a photo/video, or a contact. */
    data object ChoosingType : SendUiState

    /** Content chosen; setting up the Wi-Fi Direct group before the tap can work. */
    data class Preparing(val content: SharedContent) : SendUiState

    /** Group ready, NFC token is live: "hold the phones together". */
    data class ReadyToTap(val content: SharedContent) : SendUiState

    /** The peer connected; bytes are moving. [progress] is `0f..1f`. */
    data class Transferring(val content: SharedContent, val progress: Float) : SendUiState

    /** Done. */
    data class Sent(val content: SharedContent) : SendUiState

    /** Something went wrong; [messageRes] is user-facing copy. */
    data class Failed(
        val content: SharedContent?,
        @param:StringRes val messageRes: Int,
        val enableWifi: Boolean = false,
    ) : SendUiState
}

/** Short line shown to the receiver in the accept prompt — never the payload itself. */
fun SharedContent.handshakeSummary(): String = when (this) {
    is SharedContent.ContactCard -> "Un contact"
    is SharedContent.File -> when {
        mimeType.startsWith("video/") -> "Une vidéo"
        mimeType.startsWith("image/") -> "Une photo"
        else -> "Un fichier"
    }
}
