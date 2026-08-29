package com.tapio.core.transfer.domain

/**
 * Every way a file transfer can fail, as a closed set so the UI can map each case
 * to a specific message and recovery action.
 */
sealed class TransferError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The device has no Wi-Fi Direct support, or the system refused to start it. */
    data object WifiDirectUnavailable :
        TransferError("Wi-Fi Direct is not available on this device")

    /** A runtime permission required for Wi-Fi Direct discovery is missing. */
    data object PermissionsMissing :
        TransferError("Permission for nearby Wi-Fi devices is required")

    /** The peers did not manage to connect within the timeout. */
    data object ConnectionTimedOut :
        TransferError("Could not reach the other phone in time")

    /** The connection dropped mid-transfer. */
    data object ConnectionLost :
        TransferError("Connection to the other phone was lost")

    /** The received file's checksum does not match what the sender computed. */
    data class ChecksumMismatch(val expected: String, val actual: String) :
        TransferError("The received file is corrupted")

    /** The bytes on the wire did not match the expected framing. */
    data class MalformedStream(val reason: String) :
        TransferError("Malformed transfer stream: $reason")

    /** Any other low-level I/O failure. */
    data class Io(val ioCause: Throwable) :
        TransferError("Transfer I/O failed", ioCause)
}
