package com.tapio.app.ui.model

import androidx.annotation.StringRes
import com.tapio.app.R
import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.transfer.domain.TransferError

/** Maps the typed errors from the core modules onto user-facing string resources. */

@StringRes
fun HandshakeError.toMessageRes(): Int = when (this) {
    HandshakeError.NfcDisabled -> R.string.err_nfc_disabled
    HandshakeError.NfcUnsupported -> R.string.err_nfc_unsupported
    HandshakeError.TagLost -> R.string.err_tag_lost
    is HandshakeError.MalformedPayload -> R.string.err_handshake_malformed
    is HandshakeError.ProtocolMismatch -> R.string.err_protocol_mismatch
    is HandshakeError.Io -> R.string.err_nfc_io
}

@StringRes
fun TransferError.toMessageRes(): Int = when (this) {
    TransferError.WifiDirectUnavailable -> R.string.err_wifi_unavailable
    TransferError.PermissionsMissing -> R.string.err_permissions_missing
    TransferError.ConnectionTimedOut -> R.string.err_connection_timeout
    TransferError.ConnectionLost -> R.string.err_connection_lost
    is TransferError.ChecksumMismatch -> R.string.err_checksum_mismatch
    is TransferError.MalformedStream -> R.string.err_malformed_stream
    is TransferError.Io -> R.string.err_transfer_io
}
