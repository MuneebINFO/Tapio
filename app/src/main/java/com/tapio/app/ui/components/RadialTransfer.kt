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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Mote(val angle: Float, val phase: Float, val speed: Float, val size: Float)

/**
 * The transfer animation: a determinate ring around a central anchor, with motes
 * streaming outward (send) or spiralling inward (receive). Radial language, to
 * match [RippleBeacon] and the "phones touching" mental model.
 */
@Composable
fun RadialTransfer(
    progress: Float,
    modifier: Modifier = Modifier,
    inbound: Boolean = false,
    moteCount: Int = 34,
) {
    val ring = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val moteColor = MaterialTheme.colorScheme.secondary

    val motes = remember(moteCount) {
        val rng = Random(moteCount * 7)
        List(moteCount) {
            Mote(
                angle = rng.nextFloat() * 360f,
                phase = rng.nextFloat(),
                speed = 0.7f + rng.nextFloat() * 0.8f,
                size = 1.6f + rng.nextFloat() * 2.8f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "radial")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), label = "sweep")

    Box(modifier = modifier.size(260.dp)) {
        Canvas(Modifier.size(260.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringRadius = size.minDimension * 0.40f
            val innerRadius = size.minDimension * 0.13f

            motes.forEach { mote ->
                var travel = (t * mote.speed + mote.phase) % 1f
                if (inbound) travel = 1f - travel
                val radius = innerRadius + travel * (ringRadius - innerRadius)
                val rad = Math.toRadians((mote.angle + travel * 40f).toDouble())
                val edgeFade = (1f - kotlin.math.abs(travel - 0.5f) * 2f).coerceIn(0f, 1f)
                drawCircle(
                    color = moteColor.copy(alpha = 0.25f + edgeFade * 0.6f),
                    radius = mote.size * (0.7f + edgeFade),
                    center = center + Offset(cos(rad).toFloat() * radius, sin(rad).toFloat() * radius),
                )
            }

            drawCircle(trackColor, radius = ringRadius, style = Stroke(width = 10f))
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = 10f, cap = StrokeCap.Round),
            )
            drawCircle(ring.copy(alpha = 0.16f), radius = innerRadius + 8f, center = center)
            drawCircle(ring, radius = innerRadius, center = center)
        }
    }
}
