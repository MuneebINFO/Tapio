package com.tapio.app.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapio.app.R
import com.tapio.app.data.ActiveTransfer
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.model.SendUiState
import com.tapio.app.ui.model.handshakeSummary
import com.tapio.app.ui.model.toMessageRes
import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the send screen: choose what to share (photo/video or a phone number) →
 * NFC token live ("hold the phones together") → transfer → done / error.
 *
 * A share is a **session**: it owns a Wi-Fi Direct group and an NFC advertisement.
 * However it ends — sent, declined, failed, or abandoned — that session is torn all
 * the way down before the next one starts, otherwise the framework hands the new
 * group to a `removeGroup` still in flight from the old one and retrying never works.
 */
class SendViewModel(private val backend: TransferBackend) : ViewModel() {

    private val _state = MutableStateFlow<SendUiState>(SendUiState.ChoosingType)
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var shareJob: Job? = null

    /** Identifies the live session, so a session being torn down cannot write state. */
    private var currentSession = 0

    fun backToTypeChoice() = endSession(SendUiState.ChoosingType)

    fun onFilePicked(file: SharedContent.File) = share(file)

    fun onContactPicked(card: SharedContent.ContactCard) = share(card)

    fun retry() {
        (state.value as? SendUiState.Failed)?.content?.let(::share)
    }

    fun reset() = endSession(SendUiState.ChoosingType)

    override fun onCleared() {
        shareJob?.cancel()
    }

    private fun endSession(next: SendUiState) {
        currentSession++
        shareJob?.cancel()
        shareJob = null
        _state.value = next
    }

    private fun share(content: SharedContent) {
        val previous = shareJob
        currentSession++
        val session = currentSession
        previous?.cancel()
        _state.value = SendUiState.Preparing(content)

        val sender = backend.newSender()
        shareJob = viewModelScope.launch {
            // Wait for the old group and NFC advertisement to actually be gone.
            previous?.cancelAndJoin()

            val token = runCatching { backend.createLocalToken(content.handshakeSummary()) }.getOrElse { cause ->
                val error = cause as? TransferError
                publish(
                    session,
                    SendUiState.Failed(
                        content = content,
                        messageRes = error?.toMessageRes() ?: R.string.err_prepare_failed,
                        enableWifi = error == TransferError.WifiOff,
                    ),
                )
                return@launch
            }

            // One session per phone, whichever direction it runs in: while we send, a tap
            // from another phone must not open a receive on top of our own transfer.
            if (!ActiveTransfer.claim(token.sessionId)) {
                backend.endSession()
                publish(session, SendUiState.Failed(content, R.string.err_transfer_busy))
                return@launch
            }

            // Group is up and the token can be staged — only now is a tap meaningful.
            publish(session, SendUiState.ReadyToTap(content))
            val advertising = launch { runCatching { backend.advertiser.advertise(token) } }
            try {
                sender.send(content, token).collect { transfer ->
                    // A peer is on the line: stop handing our credentials to anyone else,
                    // so a second phone — or this one tapped again — cannot join midway.
                    if (transfer != TransferState.Connecting) advertising.cancel()
                    publish(session, transfer.toSendUiState(content))
                }
            } finally {
                // Runs on every outcome, cancellation included, and is awaited so the
                // group is really released before this job reports itself finished.
                withContext(NonCancellable) {
                    advertising.cancelAndJoin()
                    backend.endSession()
                    ActiveTransfer.release(token.sessionId)
                }
            }
        }
    }

    /** Drops emissions from a session the user has already moved on from. */
    private fun publish(session: Int, next: SendUiState) {
        if (session == currentSession) _state.value = next
    }

    private fun TransferState.toSendUiState(content: SharedContent): SendUiState = when (this) {
        TransferState.Connecting, TransferState.AwaitingPeerDecision -> SendUiState.ReadyToTap(content)
        is TransferState.PreviewReady -> SendUiState.ReadyToTap(content)
        TransferState.Declined -> SendUiState.Failed(content, R.string.send_declined)
        is TransferState.InProgress -> SendUiState.Transferring(content, progress.fraction)
        TransferState.Verifying -> SendUiState.Transferring(content, progress = 1f)
        is TransferState.Completed -> SendUiState.Sent(content)
        is TransferState.Failed -> SendUiState.Failed(
            content,
            error.toMessageRes(),
            enableWifi = error == TransferError.WifiOff,
        )
    }

    companion object {
        fun factory(backend: TransferBackend) = viewModelFactory {
            initializer { SendViewModel(backend) }
        }
    }
}
