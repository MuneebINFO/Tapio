package com.tapio.app.ui.prompt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.model.toMessageRes
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.IncomingContent
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.domain.TransferError
import com.tapio.core.transfer.domain.TransferResult
import com.tapio.core.transfer.domain.TransferState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TapioReceive"

/**
 * Drives the incoming-transfer popup: connect → ask → receive → save.
 *
 * [initialToken] is null when the tap woke the app but the handshake did not finish;
 * the popup then sits in [PromptUiState.WaitingForTap] until [onTokenRead].
 */
class TransferPromptViewModel(
    private val backend: TransferBackend,
    initialToken: SessionToken?,
) : ViewModel() {

    private val _state = MutableStateFlow<PromptUiState>(
        if (initialToken == null) PromptUiState.WaitingForTap else PromptUiState.Connecting,
    )
    val state: StateFlow<PromptUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var decision: CompletableDeferred<Boolean>? = null

    init {
        initialToken?.let(::start)
    }

    /** Called when a retry tap finally yields a token. Ignored once a transfer is running. */
    fun onTokenRead(token: SessionToken) {
        if (job != null) return
        _state.value = PromptUiState.Connecting
        start(token)
    }

    private fun start(token: SessionToken) {
        Log.i(TAG, "prompt starting for ${token.deviceName}")
        val receiver = backend.newReceiver()
        job = viewModelScope.launch {
            receiver.receive(token) { preview ->
                Log.i(TAG, "preview: ${preview.displayName}")
                _state.value = PromptUiState.Asking(token.deviceName, preview.toPromptPreview())
                CompletableDeferred<Boolean>().also { decision = it }.await()
            }.collect { transfer -> _state.value = reduce(transfer) }
        }
    }

    fun accept() {
        decision?.complete(true)
    }

    fun refuse() {
        val pending = decision
        if (pending != null && !pending.isCompleted) {
            pending.complete(false)
        } else {
            job?.cancel()
            _state.value = PromptUiState.Done(R.string.prompt_declined)
        }
    }

    fun saveFile() {
        val arrived = state.value as? PromptUiState.FileArrived ?: return
        viewModelScope.launch {
            _state.value = runCatching { arrived.file.save() }.fold(
                onSuccess = { PromptUiState.Done(R.string.prompt_saved) },
                onFailure = { PromptUiState.Failed(R.string.err_save_failed) },
            )
        }
    }

    fun discardArrival() {
        val current = state.value
        viewModelScope.launch {
            if (current is PromptUiState.FileArrived) runCatching { current.file.discard() }
            _state.value = PromptUiState.Done(R.string.prompt_declined)
        }
    }

    fun onContactHandedOff() {
        _state.value = PromptUiState.Done(R.string.prompt_contact_opened)
    }

    override fun onCleared() {
        job?.cancel()
    }

    private fun reduce(transfer: TransferState): PromptUiState = when (transfer) {
        TransferState.Connecting,
        TransferState.AwaitingPeerDecision,
        is TransferState.PreviewReady,
        -> state.value

        TransferState.Declined -> PromptUiState.Done(R.string.prompt_declined)
        is TransferState.InProgress -> PromptUiState.Receiving(transfer.progress.fraction)
        TransferState.Verifying -> PromptUiState.Verifying

        is TransferState.Completed -> when (val outcome = transfer.result) {
            is TransferResult.Received -> when (val content = outcome.content) {
                is IncomingContent.File -> PromptUiState.FileArrived(content)
                is IncomingContent.Contact -> PromptUiState.ContactArrived(content)
            }

            is TransferResult.Sent -> PromptUiState.Failed(R.string.err_transfer_io)
        }

        is TransferState.Failed -> {
            Log.w(TAG, "transfer failed: ${transfer.error.message}")
            PromptUiState.Failed(
                transfer.error.toMessageRes(),
                enableWifi = transfer.error == TransferError.WifiOff,
            )
        }
    }

    private fun ContentPreview.toPromptPreview() = PromptPreview(
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        thumbnailJpeg = thumbnailJpeg,
    )

    companion object {
        fun factory(backend: TransferBackend, token: SessionToken?) = viewModelFactory {
            initializer { TransferPromptViewModel(backend, token) }
        }
    }
}
