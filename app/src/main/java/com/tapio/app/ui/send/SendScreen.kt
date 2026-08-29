package com.tapio.app.ui.send

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.TapioTopBar
import com.tapio.app.ui.components.ThumbnailImage
import com.tapio.app.ui.components.TransferBeam
import com.tapio.app.ui.components.formatBytes
import com.tapio.app.ui.haptics.rememberTapioHaptics
import com.tapio.app.ui.model.SendUiState
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.common.SharedContent

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

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onFilePicked(it.toSharedContent(context)) }
    }

    LaunchedEffect(state::class) {
        when (state) {
            is SendUiState.ReadyToTap -> haptics.contact()
            is SendUiState.Sent -> haptics.success()
            is SendUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TapioTopBar(stringResource(R.string.send_title), onBack) },
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
                    SendUiState.PickingFile -> PickFile(
                        onPick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                    )

                    is SendUiState.ReadyToTap -> ReadyToTap(current.file)
                    is SendUiState.Transferring -> Transferring(current.file, current.progress)
                    is SendUiState.Sent -> Sent(onAnother = viewModel::reset, onDone = onBack)
                    is SendUiState.Failed -> Failed(
                        messageRes = current.messageRes,
                        onRetry = viewModel::retry,
                        onBack = onBack,
                    )
                }
            }

            val demo = backend.demo
            if (demo != null && state is SendUiState.ReadyToTap) {
                DemoHint(
                    text = stringResource(R.string.demo_peer_pickup),
                    onClick = demo::peerPicksUpFile,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun PickFile(onPick: () -> Unit) {
    CenteredColumn {
        Text(
            text = stringResource(R.string.send_pick_prompt),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onPick) { Text(stringResource(R.string.send_pick_button)) }
    }
}

@Composable
private fun ReadyToTap(file: SharedContent.File) {
    CenteredColumn {
        RippleBeacon(modifier = Modifier.size(200.dp))
        Text(stringResource(R.string.send_ready), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.send_ready_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FileRow(file)
    }
}

@Composable
private fun Transferring(file: SharedContent.File, progress: Float) {
    CenteredColumn {
        Text(stringResource(R.string.send_transferring), style = MaterialTheme.typography.headlineSmall)
        TransferBeam(progress = progress, modifier = Modifier.fillMaxWidth())
        FileRow(file)
    }
}

@Composable
private fun Sent(onAnother: () -> Unit, onDone: () -> Unit) {
    CenteredColumn {
        Text(stringResource(R.string.send_done), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onAnother) { Text(stringResource(R.string.send_another)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
    }
}

@Composable
private fun Failed(messageRes: Int, onRetry: () -> Unit, onBack: () -> Unit) {
    CenteredColumn {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}

@Composable
private fun FileRow(file: SharedContent.File) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThumbnailImage(
            uri = file.uri,
            mimeType = file.mimeType,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Column {
            Text(file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                formatBytes(file.byteSize),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun DemoHint(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.padding(bottom = 24.dp)) {
        Text("▶ $text", style = MaterialTheme.typography.labelLarge)
    }
}

private val previewFile = SharedContent.File("content://x", "vacances.jpg", "image/jpeg", 1_887_436)

@Preview
@Composable
private fun ReadyPreview() = TapioTheme(dynamicColor = false) { ReadyToTap(previewFile) }

@Preview
@Composable
private fun TransferringPreview() = TapioTheme(dynamicColor = false) { Transferring(previewFile, 0.42f) }

@Preview
@Composable
private fun SentPreview() = TapioTheme(dynamicColor = false) { Sent({}, {}) }
