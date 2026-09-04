package com.tapio.app.ui.prompt

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.R
import com.tapio.app.ui.components.AnimatedStatus
import com.tapio.app.ui.components.ErrorMark
import com.tapio.app.ui.components.RadialTransfer
import com.tapio.app.ui.components.RippleBeacon
import com.tapio.app.ui.components.SuccessMark
import com.tapio.app.ui.components.ThumbnailImage
import com.tapio.app.ui.components.formatBytes
import com.tapio.app.ui.theme.TapioTheme

/**
 * The floating card Tapio shows over whatever is on screen when someone taps their
 * phone against yours. It is the whole receive experience — no full app, no system
 * notification.
 */
@Composable
fun TransferPrompt(
    state: PromptUiState,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onAddContact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, tween(340, easing = EaseOutBack)) }

    // No scrim: the card floats over whatever the user was doing, untouched behind it.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.92f)
                .padding(horizontal = 8.dp)
                .scale(0.88f + 0.12f * entry.value)
                .alpha(entry.value),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 18.dp,
        ) {
            AnimatedStatus(targetState = state) { current ->
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (current) {
                        PromptUiState.WaitingForTap -> Body(
                            visual = { RippleBeacon(Modifier.size(120.dp)) },
                            title = stringResource(R.string.prompt_tap_again),
                            subtitle = stringResource(R.string.prompt_tap_again_hint),
                        )

                        PromptUiState.Connecting -> Body(
                            visual = { RippleBeacon(Modifier.size(120.dp)) },
                            title = stringResource(R.string.prompt_connecting),
                        )

                        is PromptUiState.Asking -> Asking(current, onAccept, onRefuse)

                        is PromptUiState.Receiving -> {
                            val p by animateFloatAsState(current.progress, label = "p")
                            Body(
                                visual = { RadialTransfer(p, Modifier.size(150.dp), inbound = true) },
                                title = stringResource(R.string.prompt_receiving),
                                subtitle = "${(p * 100).toInt()} %",
                            )
                        }

                        PromptUiState.Verifying -> Body(
                            visual = { RadialTransfer(1f, Modifier.size(150.dp), inbound = true) },
                            title = stringResource(R.string.prompt_verifying),
                        )

                        is PromptUiState.FileArrived -> SaveFile(current, onSave, onDiscard)
                        is PromptUiState.ContactArrived -> SaveContact(current, onAddContact, onDiscard)

                        is PromptUiState.Done -> Body(
                            visual = { SuccessMark() },
                            title = stringResource(current.messageRes),
                        )

                        is PromptUiState.Failed -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Body(visual = { ErrorMark() }, title = stringResource(current.messageRes))
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.prompt_close))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Asking(state: PromptUiState.Asking, onAccept: () -> Unit, onRefuse: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Preview(state.preview)
        Text(
            stringResource(R.string.prompt_wants_to_share, state.deviceName),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            describe(state.preview),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Choice(
            refuseLabel = stringResource(R.string.prompt_refuse),
            acceptLabel = stringResource(R.string.prompt_accept),
            onRefuse = onRefuse,
            onAccept = onAccept,
        )
    }
}

@Composable
private fun SaveFile(state: PromptUiState.FileArrived, onSave: () -> Unit, onDiscard: () -> Unit) {
    val header = state.file.header
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThumbnailImage("", header.mimeType, null, Modifier.size(132.dp))
        Text(
            stringResource(R.string.prompt_save_file),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "${header.displayName} · ${formatBytes(header.sizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Choice(
            refuseLabel = stringResource(R.string.prompt_refuse),
            acceptLabel = stringResource(R.string.prompt_save),
            onRefuse = onDiscard,
            onAccept = onSave,
        )
    }
}

@Composable
private fun SaveContact(state: PromptUiState.ContactArrived, onAdd: () -> Unit, onDiscard: () -> Unit) {
    val card = state.contact.card
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThumbnailImage("", "application/vnd.tapio.contact", null, Modifier.size(132.dp))
        Text(
            stringResource(R.string.prompt_save_contact),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "${card.displayName} · ${card.phoneNumber}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Choice(
            refuseLabel = stringResource(R.string.prompt_refuse),
            acceptLabel = stringResource(R.string.prompt_add),
            onRefuse = onDiscard,
            onAccept = onAdd,
        )
    }
}

@Composable
private fun Preview(preview: PromptPreview) {
    if (preview.isContact) {
        RippleBeacon(Modifier.size(132.dp))
    } else {
        ThumbnailImage(
            uri = "",
            mimeType = preview.mimeType,
            contentDescription = stringResource(R.string.save_thumbnail_cd),
            modifier = Modifier.size(132.dp),
            jpegBytes = preview.thumbnailJpeg,
        )
    }
}

@Composable
private fun describe(preview: PromptPreview): String = when {
    preview.isContact -> preview.displayName
    preview.sizeBytes > 0 -> "${preview.displayName} · ${formatBytes(preview.sizeBytes)}"
    else -> preview.displayName
}

@Composable
private fun Body(visual: @Composable () -> Unit, title: String, subtitle: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { visual() }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Choice(
    refuseLabel: String,
    acceptLabel: String,
    onRefuse: () -> Unit,
    onAccept: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onRefuse,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
        ) { Text(refuseLabel) }
        Button(
            onClick = onAccept,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
        ) { Text(acceptLabel, style = MaterialTheme.typography.labelLarge) }
    }
}

@Preview
@Composable
private fun TransferPromptPreview() = TapioTheme {
    TransferPrompt(
        state = PromptUiState.Asking(
            "Galaxy S21",
            PromptPreview("vacances-2026.jpg", "image/jpeg", 2_400_000, null),
        ),
        onAccept = {}, onRefuse = {}, onSave = {}, onDiscard = {}, onAddContact = {}, onDismiss = {},
    )
}
