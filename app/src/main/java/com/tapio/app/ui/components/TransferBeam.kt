package com.tapio.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val phaseOffset: Float, val speed: Float, val lane: Float, val radius: Float)

/**
 * A stream of particles flowing from one edge to the other, its intensity tied to
 * [progress]. Deliberately not a plain progress bar — this is the moment the file
 * is "flying" between the phones.
 *
 * @param reversed flow right-to-left (receive) instead of left-to-right (send).
 */
@Composable
fun TransferBeam(
    progress: Float,
    modifier: Modifier = Modifier,
    reversed: Boolean = false,
    particleCount: Int = 26,
) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    val particles = remember(particleCount) {
        val rng = Random(particleCount)
        List(particleCount) {
            Particle(
                phaseOffset = rng.nextFloat(),
                speed = 0.6f + rng.nextFloat() * 0.9f,
                lane = rng.nextFloat(),
                radius = 1.5f + rng.nextFloat() * 3f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "beam")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val fill by animateFloatAsState(progress.coerceIn(0f, 1f), label = "fill")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val laneHeight = size.height
            val mid = laneHeight / 2f

            drawLine(
                color = track,
                start = androidx.compose.ui.geometry.Offset(0f, mid),
                end = androidx.compose.ui.geometry.Offset(size.width, mid),
                strokeWidth = laneHeight,
            )

            particles.forEach { particle ->
                var t = (phase * particle.speed + particle.phaseOffset) % 1f
                if (reversed) t = 1f - t
                // Emit fewer particles early in the transfer, the full stream near the end.
                if (particle.phaseOffset > 0.15f + fill * 0.85f) return@forEach

                val x = size.width * t
                val wobble = sin((t + particle.phaseOffset) * 6.28f) * (laneHeight * 0.18f)
                val y = mid + (particle.lane - 0.5f) * laneHeight * 0.5f + wobble
                val edgeFade = (1f - kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0f, 1f)

                drawCircle(
                    color = color.copy(alpha = 0.25f + edgeFade * 0.6f),
                    radius = particle.radius * (0.6f + edgeFade),
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }

        Text(
            text = "${(fill * 100).toInt()} %",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
