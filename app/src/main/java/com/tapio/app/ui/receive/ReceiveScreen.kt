package com.tapio.app.ui.receive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.tapio.app.ui.components.ErrorMark
import com.tapio.app.ui.components.RadialTransfer
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.SuccessMark
import com.tapio.app.ui.components.TapioScaffold
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

    TapioScaffold(modifier = modifier, title = stringResource(R.string.receive_title), onBack = onBack) { content ->
        Column(modifier = content, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AnimatedStatus(targetState = state) { current -> ReceiveStage(current) }
            }
            ReceiveBottomBar(
                state = state,
                onListenAgain = viewModel::reset,
                onBack = onBack,
                onSimulateSend = backend.demo?.let { { it.peerSendsSampleFile() } },
            )
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
private fun ReceiveStage(state: ReceiveUiState) {
    when (state) {
        ReceiveUiState.WaitingForTap -> Stage(
            { RippleBeacon(modifier = Modifier.size(230.dp)) },
            stringResource(R.string.receive_waiting),
            stringResource(R.string.receive_waiting_hint),
        )

        ReceiveUiState.Connecting -> Stage(
            { RadialTransfer(progress = 0f, inbound = true, modifier = Modifier.size(250.dp)) },
            stringResource(R.string.receive_connecting),
            null,
        )

        is ReceiveUiState.Receiving -> {
            val animated by animateFloatAsState(state.progress, label = "recvProgress")
            Stage(
                { RadialTransfer(progress = animated, inbound = true, modifier = Modifier.size(250.dp)) },
                stringResource(R.string.receive_receiving),
                "${(animated * 100).toInt()} %",
            )
        }

        ReceiveUiState.Verifying -> Stage(
            { RadialTransfer(progress = 1f, inbound = true, modifier = Modifier.size(250.dp)) },
            stringResource(R.string.receive_verifying),
            null,
        )

        is ReceiveUiState.Arrived -> Stage(
            { RadialTransfer(progress = 1f, inbound = true, modifier = Modifier.size(250.dp)) },
            stringResource(R.string.receive_receiving),
            null,
        )

        is ReceiveUiState.Saved -> Stage({ SuccessMark() }, stringResource(R.string.receive_saved), null)
        ReceiveUiState.Declined -> Stage({ ErrorMark() }, stringResource(R.string.receive_declined), null)
        is ReceiveUiState.Failed -> Stage({ ErrorMark() }, stringResource(state.messageRes), null)
    }
}

@Composable
private fun Stage(visual: @Composable () -> Unit, title: String, subtitle: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { visual() }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReceiveBottomBar(
    state: ReceiveUiState,
    onListenAgain: () -> Unit,
    onBack: () -> Unit,
    onSimulateSend: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            ReceiveUiState.WaitingForTap ->
                if (onSimulateSend != null) {
                    val label = "▶  " + stringResource(R.string.demo_peer_send)
                    TextButton(onClick = onSimulateSend) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }

            is ReceiveUiState.Saved, ReceiveUiState.Declined, is ReceiveUiState.Failed -> {
                Button(
                    onClick = onListenAgain,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.receive_listen_again), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
            }

            else -> Unit
        }
    }
}

@Preview
@Composable
private fun WaitingPreview() = TapioTheme {
    Stage({ RippleBeacon(Modifier.size(220.dp)) }, "En attente d'un contact…", "Approchez l'autre téléphone")
}

@Preview
@Composable
private fun ReceivingPreview() = TapioTheme {
    Stage({ RadialTransfer(0.62f, inbound = true) }, "Réception en cours…", "62 %")
}
