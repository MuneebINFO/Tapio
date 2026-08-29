package com.tapio.app.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.model.SendUiState
import com.tapio.app.ui.model.toMessageRes
import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the send screen: file picked → NFC token live ("hold the phones
 * together") → transfer → done / error.
 */
class SendViewModel(private val backend: TransferBackend) : ViewModel() {

    private val _state = MutableStateFlow<SendUiState>(SendUiState.PickingFile)
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var shareJob: Job? = null

    fun onFilePicked(file: SharedContent.File) {
        shareJob?.cancel()
        _state.value = SendUiState.ReadyToTap(file)

        val sender = backend.newSender()
        shareJob = viewModelScope.launch {
            val token = runCatching { backend.createLocalToken() }.getOrElse {
                _state.value = SendUiState.Failed(file, R.string.err_prepare_failed)
                return@launch
            }

            val advertising = launch { runCatching { backend.advertiser.advertise(token) } }
            try {
                sender.send(file, token).collect { transfer ->
                    _state.value = transfer.toSendUiState(file)
                }
            } finally {
                advertising.cancel()
            }
        }
    }

    fun retry() {
        (state.value as? SendUiState.Failed)?.file?.let(::onFilePicked)
    }

    fun reset() {
        shareJob?.cancel()
        _state.value = SendUiState.PickingFile
    }

    override fun onCleared() {
        shareJob?.cancel()
    }

    private fun TransferState.toSendUiState(file: SharedContent.File): SendUiState = when (this) {
        TransferState.Connecting -> SendUiState.ReadyToTap(file)
        is TransferState.InProgress -> SendUiState.Transferring(file, progress.fraction)
        TransferState.Verifying -> SendUiState.Transferring(file, progress = 1f)
        is TransferState.Completed -> SendUiState.Sent(file)
        is TransferState.Failed -> SendUiState.Failed(file, error.toMessageRes())
    }

    companion object {
        fun factory(backend: TransferBackend) = viewModelFactory {
            initializer { SendViewModel(backend) }
        }
    }
}
