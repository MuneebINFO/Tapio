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
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the receive screen: listening for a tap → transfer → arrival → the user's
 * save / decline choice.
 */
class ReceiveViewModel(private val backend: TransferBackend) : ViewModel() {

    private val _state = MutableStateFlow<ReceiveUiState>(ReceiveUiState.WaitingForTap)
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    private var listenJob: Job? = null

    init {
        listen()
    }

    fun listen() {
        listenJob?.cancel()
        _state.value = ReceiveUiState.WaitingForTap

        val receiver = backend.newReceiver()
        listenJob = viewModelScope.launch {
            when (val outcome = backend.scanner.scan().first()) {
                is HandshakeOutcome.Failure ->
                    _state.value = ReceiveUiState.Failed(outcome.error.toMessageRes())

                is HandshakeOutcome.Success ->
                    receiver.receive(outcome.token).collect { transfer ->
                        _state.value = transfer.toReceiveUiState()
                    }
            }
        }
    }

    fun save() {
        val arrived = state.value as? ReceiveUiState.Arrived ?: return
        viewModelScope.launch {
            _state.value = runCatching { arrived.incoming.save() }
                .fold(
                    onSuccess = { ReceiveUiState.Saved(it) },
                    onFailure = { ReceiveUiState.Failed(R.string.err_save_failed) },
                )
        }
    }

    fun decline() {
        val arrived = state.value as? ReceiveUiState.Arrived ?: return
        viewModelScope.launch {
            runCatching { arrived.incoming.discard() }
            _state.value = ReceiveUiState.Declined
        }
    }

    fun reset() = listen()

    override fun onCleared() {
        listenJob?.cancel()
    }

    private fun TransferState.toReceiveUiState(): ReceiveUiState = when (this) {
        TransferState.Connecting -> ReceiveUiState.Connecting
        is TransferState.InProgress -> ReceiveUiState.Receiving(progress.fraction)
        TransferState.Verifying -> ReceiveUiState.Verifying
        is TransferState.Completed -> when (val outcome = result) {
            is TransferResult.Received -> ReceiveUiState.Arrived(outcome.file)
            is TransferResult.Sent -> ReceiveUiState.Failed(R.string.err_transfer_io)
        }

        is TransferState.Failed -> ReceiveUiState.Failed(error.toMessageRes())
    }

    companion object {
        fun factory(backend: TransferBackend) = viewModelFactory {
            initializer { ReceiveViewModel(backend) }
        }
    }
}
