package com.tapio.core.transfer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Tunables shared by [FileSender] and [FileReceiver]. Defaults are production
 * values; tests override [dispatcher] and [clock] for determinism.
 *
 * @property chunkSizeBytes buffer size for each read/write of the file body.
 * @property progressIntervalMs minimum gap between [com.tapio.core.transfer.domain.TransferState.InProgress] emissions.
 * @property connectTimeoutMs how long to wait for the Wi-Fi Direct connection.
 * @property clock time source for progress throttling.
 * @property dispatcher dispatcher the blocking stream work runs on.
 */
data class TransferConfig(
    val chunkSizeBytes: Int = 64 * 1024,
    val progressIntervalMs: Long = 100L,
    val connectTimeoutMs: Long = 15_000L,
    val clock: () -> Long = System::currentTimeMillis,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be positive" }
        require(progressIntervalMs >= 0L) { "progressIntervalMs must not be negative" }
        require(connectTimeoutMs > 0L) { "connectTimeoutMs must be positive" }
    }
}
