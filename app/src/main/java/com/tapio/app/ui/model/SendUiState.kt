package com.tapio.app.ui.model

import androidx.annotation.StringRes
import com.tapio.core.common.SharedContent

/** Everything the send screen can be showing, in the order it usually happens. */
sealed interface SendUiState {

    /** No file chosen yet — show the picker call to action. */
    data object PickingFile : SendUiState

    /** File chosen, NFC token is live: "hold the phones together". */
    data class ReadyToTap(val file: SharedContent.File) : SendUiState

    /** The peer connected; bytes are moving. [progress] is `0f..1f`. */
    data class Transferring(val file: SharedContent.File, val progress: Float) : SendUiState

    /** Done. */
    data class Sent(val file: SharedContent.File) : SendUiState

    /** Something went wrong; [messageRes] is user-facing copy. */
    data class Failed(
        val file: SharedContent.File?,
        @param:StringRes val messageRes: Int,
    ) : SendUiState
}
