package com.tapio.app.ui.receive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.components.AnimatedStatus
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.TapioTopBar
import com.tapio.app.ui.components.TransferBeam
import com.tapio.app.ui.haptics.rememberTapioHaptics
import com.tapio.app.ui.model.ReceiveUiState
import com.tapio.app.ui.theme.TapioTheme

@Composable
fun ReceiveScreen(
    backend: TransferBackend,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReceiveViewModel = viewModel(factory = ReceiveViewModel.factory(backend))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberTapioHaptics()

    LaunchedEffect(state::class) {
        when (state) {
            is ReceiveUiState.Connecting -> haptics.contact()
            is ReceiveUiState.Saved -> haptics.success()
            is ReceiveUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TapioTopBar(stringResource(R.string.receive_title), onBack) },
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedStatus(targetState = state) { current ->
                when (current) {
                    ReceiveUiState.WaitingForTap -> Waiting()
                    ReceiveUiState.Connecting -> Message(stringResource(R.string.receive_connecting))
                    is ReceiveUiState.Receiving -> Receiving(current.progress)
                    ReceiveUiState.Verifying -> Message(stringResource(R.string.receive_verifying))
                    is ReceiveUiState.Arrived -> Message(stringResource(R.string.receive_receiving))
                    is ReceiveUiState.Saved -> Done(
                        text = stringResource(R.string.receive_saved),
                        onAgain = viewModel::reset,
                        onBack = onBack,
                    )

                    ReceiveUiState.Declined -> Done(
                        text = stringResource(R.string.receive_declined),
                        onAgain = viewModel::reset,
                        onBack = onBack,
                    )

                    is ReceiveUiState.Failed -> Failed(current.messageRes, viewModel::reset, onBack)
                }
            }

            val demo = backend.demo
            if (demo != null && state is ReceiveUiState.WaitingForTap) {
                TextButton(
                    onClick = demo::peerSendsSampleFile,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                ) {
                    Text("▶ " + stringResource(R.string.demo_peer_send), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    (state as? ReceiveUiState.Arrived)?.let { arrived ->
        SaveFileDialog(
            header = arrived.incoming.header,
            onAccept = viewModel::save,
            onDecline = viewModel::decline,
        )
    }
}

@Composable
private fun Waiting() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        RippleBeacon(modifier = Modifier.size(200.dp))
        Text(stringResource(R.string.receive_waiting), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.receive_waiting_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Receiving(progress: Float) {
    val animated by animateFloatAsState(progress, label = "receiveProgress")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.receive_receiving), style = MaterialTheme.typography.headlineSmall)
        TransferBeam(progress = animated, reversed = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
private fun Done(text: String, onAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onAgain) { Text(stringResource(R.string.receive_listen_again)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
    }
}

@Composable
private fun Failed(messageRes: Int, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}

@Preview
@Composable
private fun WaitingPreview() = TapioTheme(dynamicColor = false) { Waiting() }

@Preview
@Composable
private fun ReceivingPreview() = TapioTheme(dynamicColor = false) { Receiving(0.6f) }
