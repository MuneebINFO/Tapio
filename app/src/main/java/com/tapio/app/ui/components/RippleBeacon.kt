package com.tapio.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A slow pulse of expanding rings around a solid core — the "a tap is expected now"
 * signal, shown while the app waits for the phones to touch.
 */
@Composable
fun RippleBeacon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    ringCount: Int = 3,
) {
    val tint = if (color == Color.Unspecified) {
        androidx.compose.material3.MaterialTheme.colorScheme.primary
    } else {
        color
    }

    val transition = rememberInfiniteTransition(label = "ripple")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier.size(220.dp)) {
        val maxRadius = min(size.width, size.height) / 2f
        val coreRadius = maxRadius * 0.16f

        repeat(ringCount) { index ->
            val offset = index.toFloat() / ringCount
            val t = (phase + offset) % 1f
            drawCircle(
                color = tint.copy(alpha = (1f - t) * 0.5f),
                radius = coreRadius + t * (maxRadius - coreRadius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = maxRadius * 0.03f),
            )
        }
        drawCircle(color = tint, radius = coreRadius)
    }
}
