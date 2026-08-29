package com.tapio.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.tapio.app.ui.theme.BrandCyan
import com.tapio.app.ui.theme.BrandIndigo
import kotlin.math.cos
import kotlin.math.sin

/**
 * The house background: the theme surface with two very slow, very soft drifting
 * glows. Sits behind every screen so the app feels like one continuous space.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.background

    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(base)

            val a = Offset(
                x = size.width * (0.28f + 0.10f * cos(drift)),
                y = size.height * (0.22f + 0.08f * sin(drift)),
            )
            val b = Offset(
                x = size.width * (0.78f + 0.10f * cos(drift + 2.1f)),
                y = size.height * (0.80f + 0.08f * sin(drift + 2.1f)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BrandIndigo.copy(alpha = 0.16f), Color.Transparent),
                    center = a,
                    radius = size.minDimension * 0.9f,
                ),
                radius = size.minDimension * 0.9f,
                center = a,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BrandCyan.copy(alpha = 0.10f), Color.Transparent),
                    center = b,
                    radius = size.minDimension * 0.8f,
                ),
                radius = size.minDimension * 0.8f,
                center = b,
            )
        }
        content()
    }
}
