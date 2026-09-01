package com.tapio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.common.SharedContent

/** One card that summarises whatever is being shared — a file or a contact. */
@Composable
fun ContentSummaryCard(content: SharedContent, modifier: Modifier = Modifier) {
    when (content) {
        is SharedContent.File -> FileCard(
            displayName = content.displayName,
            mimeType = content.mimeType,
            sizeBytes = content.byteSize,
            modifier = modifier,
            thumbnailUri = content.uri,
        )

        is SharedContent.ContactCard -> ContactCard(
            name = content.displayName,
            phoneNumber = content.phoneNumber,
            organization = content.organization,
            modifier = modifier,
        )
    }
}

@Composable
fun ContactCard(
    name: String,
    phoneNumber: String,
    organization: String?,
    modifier: Modifier = Modifier,
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
            PersonAvatar(modifier = Modifier.size(52.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(phoneNumber, organization).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PersonAvatar(modifier: Modifier = Modifier) {
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) { drawPerson(fg) }
        }
    }
}

private fun DrawScope.drawPerson(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.19f, center = Offset(w * 0.5f, h * 0.30f))
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = true,
        topLeft = Offset(w * 0.14f, h * 0.52f),
        size = Size(w * 0.72f, h * 0.7f),
    )
}

@Preview
@Composable
private fun ContactCardPreview() {
    TapioTheme {
        ContactCard("Jean Dupont", "+33 6 12 34 56 78", "Café des Amis", modifier = Modifier.padding(16.dp))
    }
}
