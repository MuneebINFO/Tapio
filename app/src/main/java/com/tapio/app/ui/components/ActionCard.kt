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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.ui.theme.TapioTheme

/** Which way the arrow glyph on an [ActionCard] points. */
enum class ActionDirection { SEND, RECEIVE }

/**
 * A large tappable card for a primary choice on the home screen — glyph, title and
 * a one-line description, in the style of Quick Share / Nearby Share entry points.
 */
@Composable
fun ActionCard(
    title: String,
    description: String,
    direction: ActionDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {}
                val glyphColor = MaterialTheme.colorScheme.onPrimaryContainer
                Canvas(Modifier.size(24.dp)) { drawArrowGlyph(glyphColor, direction) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawArrowGlyph(color: Color, direction: ActionDirection) {
    val w = size.width
    val h = size.height
    val sw = w * 0.14f
    val up = direction == ActionDirection.SEND

    val tipY = if (up) h * 0.10f else h * 0.90f
    val baseY = if (up) h * 0.90f else h * 0.10f
    val headY = if (up) h * 0.42f else h * 0.58f

    drawLine(color, Offset(w * 0.5f, baseY), Offset(w * 0.5f, tipY), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.22f, headY), Offset(w * 0.5f, tipY), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.78f, headY), Offset(w * 0.5f, tipY), sw, StrokeCap.Round)
}

@Preview
@Composable
private fun ActionCardPreview() {
    TapioTheme {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("Envoyer", "Choisir une photo ou une vidéo", ActionDirection.SEND, {})
            ActionCard("Recevoir", "Attendre un fichier d'un autre téléphone", ActionDirection.RECEIVE, {})
        }
    }
}
