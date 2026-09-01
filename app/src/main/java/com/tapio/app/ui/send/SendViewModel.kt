package com.tapio.app.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.model.SendUiState
import com.tapio.app.ui.model.handshakeSummary
import com.tapio.app.ui.model.toMessageRes
import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the send screen: choose what to share (photo/video or a phone number) →
 * NFC token live ("hold the phones together") → transfer → done / error.
 */
class SendViewModel(private val backend: TransferBackend) : ViewModel() {

    private val _state = MutableStateFlow<SendUiState>(SendUiState.ChoosingType)
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var shareJob: Job? = null

    fun chooseContact() {
        _state.value = SendUiState.EnteringContact
    }

    fun backToTypeChoice() {
        shareJob?.cancel()
        _state.value = SendUiState.ChoosingType
    }

    fun onFilePicked(file: SharedContent.File) = share(file)

    fun onContactEntered(name: String, phoneNumber: String, organization: String?) {
        val trimmedName = name.trim()
        val trimmedNumber = phoneNumber.trim()
        if (trimmedName.isEmpty() || trimmedNumber.isEmpty()) return
        share(SharedContent.ContactCard(trimmedName, trimmedNumber, organization?.trim()?.ifBlank { null }))
    }

    fun retry() {
        (state.value as? SendUiState.Failed)?.content?.let(::share)
    }

    fun reset() {
        shareJob?.cancel()
        _state.value = SendUiState.ChoosingType
    }

    override fun onCleared() {
        shareJob?.cancel()
    }

    private fun share(content: SharedContent) {
        shareJob?.cancel()
        _state.value = SendUiState.ReadyToTap(content)

        val sender = backend.newSender()
        shareJob = viewModelScope.launch {
            val token = runCatching { backend.createLocalToken(content.handshakeSummary()) }.getOrElse {
                _state.value = SendUiState.Failed(content, R.string.err_prepare_failed)
                return@launch
            }

            val advertising = launch { runCatching { backend.advertiser.advertise(token) } }
            try {
                sender.send(content, token).collect { transfer ->
                    _state.value = transfer.toSendUiState(content)
                }
            } finally {
                advertising.cancel()
            }
        }
    }

    private fun TransferState.toSendUiState(content: SharedContent): SendUiState = when (this) {
        TransferState.Connecting -> SendUiState.ReadyToTap(content)
        is TransferState.InProgress -> SendUiState.Transferring(content, progress.fraction)
        TransferState.Verifying -> SendUiState.Transferring(content, progress = 1f)
        is TransferState.Completed -> SendUiState.Sent(content)
        is TransferState.Failed -> SendUiState.Failed(content, error.toMessageRes())
    }

    companion object {
        fun factory(backend: TransferBackend) = viewModelFactory {
            initializer { SendViewModel(backend) }
        }
    }
}
