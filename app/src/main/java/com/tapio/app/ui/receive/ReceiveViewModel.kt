package com.tapio.app.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.model.ReceiveUiState
import com.tapio.app.ui.model.toMessageRes
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.IncomingContent
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the receive screen: a tap (in-app, or the one that launched the app) →
 * **accept prompt** → transfer → the user's save / decline choice.
 *
 * @param incomingToken non-null when the app was opened by an NFC tap; the flow
 *   then starts straight at [ReceiveUiState.AwaitingAcceptance].
 */
class ReceiveViewModel(
    private val backend: TransferBackend,
    incomingToken: SessionToken? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiveUiState>(ReceiveUiState.WaitingForTap)
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var receiveJob: Job? = null
    private var pendingToken: SessionToken? = null

    /**
     * Bound before the accept prompt is shown and reused by [accept] — creating it
     * later would rotate the fake backend's in-process plumbing out from under the
     * already-open connection.
     */
    private var receiver: FileReceiver? = null

    init {
        if (incomingToken != null) {
            receiver = backend.newReceiver()
            promptFor(incomingToken)
        } else {
            listen()
        }
    }

    fun listen() {
        cancelJobs()
        _state.value = ReceiveUiState.WaitingForTap
        receiver = backend.newReceiver()
        listenJob = viewModelScope.launch {
            when (val outcome = backend.scanner.scan().first()) {
                is HandshakeOutcome.Failure -> _state.value = ReceiveUiState.Failed(outcome.error.toMessageRes())
                is HandshakeOutcome.Success -> promptFor(outcome.token)
            }
        }
    }

    fun accept() {
        val token = pendingToken ?: return
        val activeReceiver = receiver ?: return
        receiveJob = viewModelScope.launch {
            activeReceiver.receive(token).collect { transfer -> _state.value = transfer.toReceiveUiState() }
        }
    }

    fun refuse() {
        cancelJobs()
        _state.value = ReceiveUiState.Declined
    }

    /** "Save this file?" → yes: persist to the gallery. */
    fun acceptFile() {
        val arrived = state.value as? ReceiveUiState.FileArrived ?: return
        viewModelScope.launch {
            _state.value = runCatching { arrived.file.save() }.fold(
                onSuccess = { ReceiveUiState.Saved(R.string.receive_saved) },
                onFailure = { ReceiveUiState.Failed(R.string.err_save_failed) },
            )
        }
    }

    /** The screen has launched the system "add contact" screen for the arrived card. */
    fun onContactHandedOff() {
        _state.value = ReceiveUiState.Saved(R.string.receive_contact_saved)
    }

    /** "Save this?" → no. */
    fun declineArrival() {
        val current = state.value
        viewModelScope.launch {
            if (current is ReceiveUiState.FileArrived) runCatching { current.file.discard() }
            _state.value = ReceiveUiState.Declined
        }
    }

    fun reset() = listen()

    override fun onCleared() = cancelJobs()

    private fun promptFor(token: SessionToken) {
        pendingToken = token
        _state.value = ReceiveUiState.AwaitingAcceptance(token.deviceName, token.payloadSummary)
    }

    private fun cancelJobs() {
        listenJob?.cancel()
        receiveJob?.cancel()
    }

    private fun TransferState.toReceiveUiState(): ReceiveUiState = when (this) {
        TransferState.Connecting -> ReceiveUiState.Connecting
        is TransferState.InProgress -> ReceiveUiState.Receiving(progress.fraction)
        TransferState.Verifying -> ReceiveUiState.Verifying
        is TransferState.Completed -> when (val outcome = result) {
            is TransferResult.Received -> when (val content = outcome.content) {
                is IncomingContent.File -> ReceiveUiState.FileArrived(content)
                is IncomingContent.Contact -> ReceiveUiState.ContactArrived(content)
            }

            is TransferResult.Sent -> ReceiveUiState.Failed(R.string.err_transfer_io)
        }

        is TransferState.Failed -> ReceiveUiState.Failed(error.toMessageRes())
    }

    companion object {
        fun factory(backend: TransferBackend, incomingToken: SessionToken? = null) = viewModelFactory {
            initializer { ReceiveViewModel(backend, incomingToken) }
        }
    }
}
