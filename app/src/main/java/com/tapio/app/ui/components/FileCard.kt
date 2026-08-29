package com.tapio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.ui.theme.TapioTheme

/** Compact card showing a file's preview, name and size — used across the flows. */
@Composable
fun FileCard(
    displayName: String,
    mimeType: String,
    sizeBytes: Long?,
    modifier: Modifier = Modifier,
    thumbnailUri: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ThumbnailImage(
                uri = thumbnailUri.orEmpty(),
                mimeType = mimeType,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${fileTypeLabel(mimeType)} · ${formatBytes(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun fileTypeLabel(mimeType: String): String =
    mimeType.substringAfterLast('/', missingDelimiterValue = "fichier").uppercase()

@Preview
@Composable
private fun FileCardPreview() {
    TapioTheme {
        FileCard("vacances-2026.jpg", "image/jpeg", 1_887_436, modifier = Modifier.padding(16.dp))
    }
}
