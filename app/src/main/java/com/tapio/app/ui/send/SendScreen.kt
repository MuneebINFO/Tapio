package com.tapio.app.ui.send

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapio.app.R
import com.tapio.app.data.TransferBackend
import com.tapio.app.data.toSharedContent
import com.tapio.app.ui.components.AnimatedStatus
import com.tapio.app.ui.components.ErrorMark
import com.tapio.app.ui.components.FileCard
import com.tapio.app.ui.components.RadialTransfer
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.SuccessMark
import com.tapio.app.ui.components.TapioScaffold
import com.tapio.app.ui.haptics.rememberTapioHaptics
import com.tapio.app.ui.model.SendUiState
import com.tapio.app.ui.theme.TapioTheme

@Composable
fun SendScreen(
    backend: TransferBackend,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SendViewModel = viewModel(factory = SendViewModel.factory(backend))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberTapioHaptics()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toSharedContent(context)) }
    }
    fun launchPicker() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
    )

    LaunchedEffect(state::class) {
        when (state) {
            is SendUiState.ReadyToTap -> haptics.contact()
            is SendUiState.Sent -> haptics.success()
            is SendUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    TapioScaffold(modifier = modifier, title = stringResource(R.string.send_title), onBack = onBack) { content ->
        Column(modifier = content, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AnimatedStatus(targetState = state) { current ->
                    when (current) {
                        SendUiState.PickingFile -> PickStage()
                        is SendUiState.ReadyToTap -> Stage(
                            visual = { RippleBeacon(modifier = Modifier.size(230.dp)) },
                            title = stringResource(R.string.send_ready),
                            subtitle = stringResource(R.string.send_ready_hint),
                        )

                        is SendUiState.Transferring -> Stage(
                            visual = { RadialTransfer(progress = current.progress, modifier = Modifier.size(250.dp)) },
                            title = stringResource(R.string.send_transferring),
                            subtitle = "${(current.progress * 100).toInt()} %",
                        )

                        is SendUiState.Sent -> Stage(
                            visual = { SuccessMark() },
                            title = stringResource(R.string.send_done),
                            subtitle = null,
                        )

                        is SendUiState.Failed -> Stage(
                            visual = { ErrorMark() },
                            title = stringResource(current.messageRes),
                            subtitle = null,
                        )
                    }
                }
            }

            BottomBar(
                state = state,
                onPick = ::launchPicker,
                onRetry = viewModel::retry,
                onAnother = viewModel::reset,
                onBack = onBack,
                onSimulateContact = backend.demo?.let { { it.peerPicksUpFile() } },
            )
        }
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
private fun PickStage() {
    val transition = rememberInfiniteTransition(label = "pickHint")
    val glow by transition.animateFloat(
        0.4f,
        1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RippleBeacon(modifier = Modifier.size(180.dp).alpha(glow))
        Text(
            stringResource(R.string.send_pick_prompt),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BottomBar(
    state: SendUiState,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit,
    onSimulateContact: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val file = (state as? SendUiState.ReadyToTap)?.file
            ?: (state as? SendUiState.Transferring)?.file
        if (file != null) {
            FileCard(file.displayName, file.mimeType, file.byteSize, thumbnailUri = file.uri)
        }

        when (state) {
            SendUiState.PickingFile -> PrimaryButton(stringResource(R.string.send_pick_button), onPick)
            is SendUiState.ReadyToTap -> {
                if (onSimulateContact != null) {
                    DemoButton(stringResource(R.string.demo_peer_pickup), onSimulateContact)
                }
            }

            is SendUiState.Transferring -> Unit
            is SendUiState.Sent -> {
                PrimaryButton(stringResource(R.string.send_another), onAnother)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
            }

            is SendUiState.Failed -> {
                PrimaryButton(stringResource(R.string.action_retry), onRetry)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DemoButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("▶  $text", style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun ReadyPreview() = TapioTheme {
    Column(Modifier.fillMaxSize()) {
        Stage({ RippleBeacon(Modifier.size(200.dp)) }, "Approchez les téléphones", "Gardez-les en contact")
    }
}

@Preview
@Composable
private fun TransferringPreview() = TapioTheme {
    Stage({ RadialTransfer(0.46f, Modifier.size(240.dp)) }, "Envoi en cours…", "46 %")
}

@Preview
@Composable
private fun DonePreview() = TapioTheme { Stage({ SuccessMark() }, "Envoyé", null) }
