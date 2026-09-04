package com.tapio.app.ui.send

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.tapio.app.data.PickPhoneNumber
import com.tapio.app.data.TransferBackend
import com.tapio.app.data.WifiPanel
import com.tapio.app.data.readPickedNumber
import com.tapio.app.data.toSharedContent
import com.tapio.app.ui.components.ActionCard
import com.tapio.app.ui.components.ActionDirection
import com.tapio.app.ui.components.AnimatedStatus
import com.tapio.app.ui.components.ContentSummaryCard
import com.tapio.app.ui.components.ErrorMark
import com.tapio.app.ui.components.RadialTransfer
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.SuccessMark
import com.tapio.app.ui.components.TapioScaffold
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

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toSharedContent(context)) }
    }
    fun launchMediaPicker() = mediaPicker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
    )

    val contactPicker = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        uri?.let { context.readPickedNumber(it)?.let(viewModel::onContactPicked) }
    }
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) contactPicker.launch(Unit) }
    fun launchContactPicker() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) contactPicker.launch(Unit) else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    LaunchedEffect(state::class) {
        when (state) {
            is SendUiState.ReadyToTap -> haptics.contact()
            is SendUiState.Sent -> haptics.success()
            is SendUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    val onTopBack: () -> Unit = {
        when (state) {
            SendUiState.ChoosingType -> onBack()
            else -> viewModel.backToTypeChoice()
        }
    }

    TapioScaffold(modifier = modifier, title = stringResource(R.string.send_title), onBack = onTopBack) { content ->
        AnimatedStatus(targetState = state, modifier = content) { current ->
            when (current) {
                SendUiState.ChoosingType -> TypeChooser(
                    onPickMedia = ::launchMediaPicker,
                    onPickContact = ::launchContactPicker,
                )

                is SendUiState.Preparing -> TransferStage(
                    content = current.content,
                    visual = { RadialTransfer(progress = 0f, modifier = Modifier.size(220.dp)) },
                    title = stringResource(R.string.send_preparing),
                    subtitle = null,
                )

                is SendUiState.ReadyToTap -> TransferStage(
                    content = current.content,
                    visual = { RippleBeacon(modifier = Modifier.size(220.dp)) },
                    title = stringResource(R.string.send_ready),
                    subtitle = stringResource(R.string.send_ready_hint),
                    demoAction = backend.demo?.let {
                        stringResource(R.string.demo_peer_pickup) to it::peerPicksUpContent
                    },
                )

                is SendUiState.Transferring -> {
                    // Progress arrives in steps; ease between them so the ring sweeps
                    // instead of jumping.
                    val progress by animateFloatAsState(current.progress, label = "sendProgress")
                    TransferStage(
                        content = current.content,
                        visual = { RadialTransfer(progress = progress, modifier = Modifier.size(240.dp)) },
                        title = stringResource(R.string.send_transferring),
                        subtitle = "${(progress * 100).toInt()} %",
                    )
                }

                is SendUiState.Sent -> ResultStage(
                    visual = { SuccessMark() },
                    title = stringResource(R.string.send_done),
                    primaryLabel = stringResource(R.string.send_another),
                    onPrimary = viewModel::reset,
                    onDone = onBack,
                )

                is SendUiState.Failed -> ResultStage(
                    visual = { ErrorMark() },
                    title = stringResource(current.messageRes),
                    primaryLabel = stringResource(R.string.action_retry),
                    onPrimary = viewModel::retry,
                    onDone = onBack,
                    secondaryLabel = if (current.enableWifi) stringResource(R.string.action_enable_wifi) else null,
                    onSecondary = { WifiPanel.open(context) },
                )
            }
        }
    }
}

@Composable
private fun TypeChooser(onPickMedia: () -> Unit, onPickContact: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.send_choose_prompt),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        ActionCard(
            title = stringResource(R.string.send_choose_media),
            description = stringResource(R.string.send_choose_media_desc),
            direction = ActionDirection.SEND,
            onClick = onPickMedia,
        )
        ActionCard(
            title = stringResource(R.string.send_choose_contact),
            description = stringResource(R.string.send_choose_contact_desc),
            direction = ActionDirection.SEND,
            onClick = onPickContact,
        )
    }
}

@Composable
private fun TransferStage(
    content: SharedContent,
    visual: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    demoAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        StageColumn(visual, title, subtitle)
        Spacer(Modifier.weight(1f))
        ContentSummaryCard(content)
        if (demoAction != null) {
            TextButton(onClick = demoAction.second) {
                Text("▶  ${demoAction.first}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ResultStage(
    visual: @Composable () -> Unit,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDone: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        StageColumn(visual, title, subtitle = null)
        Spacer(Modifier.weight(1f))
        if (secondaryLabel != null) {
            Button(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(secondaryLabel, style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onPrimary) { Text(primaryLabel) }
        } else {
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge) }
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
    }
}

@Composable
private fun StageColumn(visual: @Composable () -> Unit, title: String, subtitle: String?) {
    Column(
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

@Preview
@Composable
private fun TypeChooserPreview() = TapioTheme {
    TypeChooser(onPickMedia = {}, onPickContact = {})
}

@Preview
@Composable
private fun SentPreview() = TapioTheme {
    ResultStage({ SuccessMark() }, "Envoyé", "Partager autre chose", {}, {})
}
