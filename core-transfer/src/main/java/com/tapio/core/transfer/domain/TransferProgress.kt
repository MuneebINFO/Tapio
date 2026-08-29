package com.tapio.core.transfer.domain

/**
 * A point-in-time snapshot of a running transfer, emitted repeatedly on
 * [TransferState.InProgress] so the UI can drive its animation.
 *
 * @property bytesTransferred bytes moved so far.
 * @property totalBytes expected total; `0` or negative if the size was unknown up front.
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    /** Completion ratio in `0f..1f`; `0f` when the total is unknown. */
    val fraction: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (bytesTransferred.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}
