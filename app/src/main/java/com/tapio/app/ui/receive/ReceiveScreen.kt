package com.tapio.app.ui.receive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapio.app.R
import com.tapio.app.data.ContactSaver
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
import com.tapio.core.nfc.domain.SessionToken

@Composable
fun ReceiveScreen(
    backend: TransferBackend,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    incomingToken: SessionToken? = null,
) {
    val viewModel: ReceiveViewModel = viewModel(
        key = incomingToken?.sessionId?.toString(),
        factory = ReceiveViewModel.factory(backend, incomingToken),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberTapioHaptics()

    LaunchedEffect(state::class) {
        when (state) {
            is ReceiveUiState.AwaitingAcceptance -> haptics.contact()
            is ReceiveUiState.Saved -> haptics.success()
            is ReceiveUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    TapioScaffold(modifier = modifier, title = stringResource(R.string.receive_title), onBack = onBack) { content ->
        AnimatedStatus(targetState = state, modifier = content) { current ->
            when (current) {
                ReceiveUiState.WaitingForTap -> WaitingStage(
                    demoFile = backend.demo?.let { it::peerSendsSampleFile },
                    demoContact = backend.demo?.let { it::peerSharesSampleContact },
                )

                is ReceiveUiState.AwaitingAcceptance -> AcceptPrompt(
                    deviceName = current.deviceName,
                    summary = current.summary,
                    onAccept = viewModel::accept,
                    onRefuse = viewModel::refuse,
                )

                ReceiveUiState.Connecting -> Stage(
                    { RadialTransfer(progress = 0f, inbound = true, modifier = Modifier.size(240.dp)) },
                    stringResource(R.string.receive_connecting),
                    null,
                )

                is ReceiveUiState.Receiving -> {
                    val animated by animateFloatAsState(current.progress, label = "recvProgress")
                    Stage(
                        { RadialTransfer(progress = animated, inbound = true, modifier = Modifier.size(240.dp)) },
                        stringResource(R.string.receive_receiving),
                        "${(animated * 100).toInt()} %",
                    )
                }

                ReceiveUiState.Verifying -> Stage(
                    { RadialTransfer(progress = 1f, inbound = true, modifier = Modifier.size(240.dp)) },
                    stringResource(R.string.receive_verifying),
                    null,
                )

                is ReceiveUiState.FileArrived -> {
                    Stage(
                        { RadialTransfer(progress = 1f, inbound = true, modifier = Modifier.size(240.dp)) },
                        stringResource(R.string.receive_receiving),
                        null,
                    )
                    SaveFileDialog(
                        header = current.file.header,
                        onAccept = viewModel::acceptFile,
                        onDecline = viewModel::declineArrival,
                    )
                }

                is ReceiveUiState.ContactArrived -> {
                    Stage(
                        { RadialTransfer(progress = 1f, inbound = true, modifier = Modifier.size(240.dp)) },
                        stringResource(R.string.receive_receiving),
                        null,
                    )
                    SaveContactDialog(
                        card = current.contact.card,
                        onAccept = {
                            ContactSaver.launch(context, current.contact.card)
                            viewModel.onContactHandedOff()
                        },
                        onDecline = viewModel::declineArrival,
                    )
                }

                is ReceiveUiState.Saved -> ResultStage(
                    { SuccessMark() },
                    stringResource(current.messageRes),
                    onListenAgain = viewModel::reset,
                    onBack = onBack,
                )

                ReceiveUiState.Declined -> ResultStage(
                    { ErrorMark() },
                    stringResource(R.string.receive_declined),
                    onListenAgain = viewModel::reset,
                    onBack = onBack,
                )

                is ReceiveUiState.Failed -> ResultStage(
                    { ErrorMark() },
                    stringResource(current.messageRes),
                    onListenAgain = viewModel::reset,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun WaitingStage(demoFile: (() -> Unit)?, demoContact: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Stage(
            { RippleBeacon(modifier = Modifier.size(220.dp)) },
            stringResource(R.string.receive_waiting),
            stringResource(R.string.receive_waiting_hint),
        )
        Spacer(Modifier.weight(1f))
        if (demoFile != null) {
            DemoButton("▶  " + stringResource(R.string.demo_peer_send_file), demoFile)
        }
        if (demoContact != null) {
            DemoButton("▶  " + stringResource(R.string.demo_peer_send_contact), demoContact)
        }
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AcceptPrompt(
    deviceName: String,
    summary: String,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        RippleBeacon(modifier = Modifier.size(180.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.receive_accept_title, deviceName),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            summary,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text(stringResource(R.string.receive_accept), style = MaterialTheme.typography.labelLarge) }
        OutlinedButton(
            onClick = onRefuse,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text(stringResource(R.string.receive_refuse)) }
    }
}

@Composable
private fun Stage(visual: @Composable () -> Unit, title: String, subtitle: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
private fun ResultStage(
    visual: @Composable () -> Unit,
    title: String,
    onListenAgain: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Stage(visual, title, null)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onListenAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text(stringResource(R.string.receive_listen_again), style = MaterialTheme.typography.labelLarge) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
    }
}

@Preview
@Composable
private fun AcceptPromptPreview() = TapioTheme {
    AcceptPrompt("Téléphone de Léa", "Un contact", onAccept = {}, onRefuse = {})
}
