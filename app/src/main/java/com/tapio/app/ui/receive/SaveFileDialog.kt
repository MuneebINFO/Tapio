package com.tapio.app.ui.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.R
import com.tapio.app.ui.components.ThumbnailImage
import com.tapio.app.ui.components.formatBytes
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.transfer.domain.FileHeader

/**
 * The "Save this file?" modal shown the instant a file arrives and passes its
 * checksum. Preview + name + size, one clear accept and one clear decline.
 */
@Composable
fun SaveFileDialog(
    header: FileHeader,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.save_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThumbnailImage(
                    uri = "",
                    mimeType = header.mimeType,
                    contentDescription = stringResource(R.string.save_thumbnail_cd),
                    modifier = Modifier.size(140.dp),
                )
                Text(header.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.save_dialog_size,
                        header.mimeType,
                        formatBytes(header.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.save_dialog_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.save_dialog_decline)) }
        },
    )
}

@Preview
@Composable
private fun SaveFileDialogPreview() {
    TapioTheme(dynamicColor = false) {
        SaveFileDialog(
            header = FileHeader("photo-recue.jpg", "image/jpeg", 1_887_436),
            onAccept = {},
            onDecline = {},
        )
    }
}
