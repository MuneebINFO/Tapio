package com.tapio.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A ring that draws itself, then a check strokes in — the transfer-complete mark. */
@Composable
fun SuccessMark(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(680, easing = EaseOutCubic)) }
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier.size(104.dp)) {
        val p = progress.value
        val radius = size.minDimension * 0.42f
        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * (p.coerceAtMost(0.6f) / 0.6f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 8f, cap = StrokeCap.Round),
        )

        val check = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val a = Offset(size.width * 0.33f, size.height * 0.52f)
        val b = Offset(size.width * 0.45f, size.height * 0.64f)
        val c = Offset(size.width * 0.70f, size.height * 0.37f)
        val seg1 = (check / 0.4f).coerceIn(0f, 1f)
        val seg2 = ((check - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (seg1 > 0f) drawLine(color, a, lerp(a, b, seg1), strokeWidth = 8f, cap = StrokeCap.Round)
        if (seg2 > 0f) drawLine(color, b, lerp(b, c, seg2), strokeWidth = 8f, cap = StrokeCap.Round)
    }
}

/** A ring with an exclamation — the error mark, with a small pop-in. */
@Composable
fun ErrorMark(modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(420, easing = EaseOutBack)) }
    val color = MaterialTheme.colorScheme.error

    Canvas(
        modifier
            .size(104.dp)
            .scale(0.6f + 0.4f * pop.value),
    ) {
        val radius = size.minDimension * 0.42f
        drawCircle(color, radius = radius, style = Stroke(width = 8f))
        drawLine(
            color,
            Offset(size.width / 2f, size.height * 0.30f),
            Offset(size.width / 2f, size.height * 0.58f),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = 4.5f, center = Offset(size.width / 2f, size.height * 0.70f))
    }
}
