package com.tapio.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.tapio.app.ui.brand.drawTapioMark
import com.tapio.app.ui.theme.WordmarkStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashBackground = Color(0xFF4A5AE0)

/**
 * The animated hand-off from the system splash screen: the mark draws its arcs,
 * a ripple pushes outward, the wordmark rises, then the whole thing lifts away.
 * Starts on exactly the splash colour + mark so the transition is seamless.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val arcReveal = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { arcReveal.animateTo(1f, tween(620, easing = EaseOutCubic)) }
        delay(260)
        launch { ripple.animateTo(1f, tween(900, easing = LinearEasing)) }
        launch { wordmark.animateTo(1f, tween(460, easing = EaseOutCubic)) }
        delay(760)
        exit.animateTo(1f, tween(360, easing = EaseOutCubic))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                val lift = -40f * exit.value
                translationY = lift
                val s = 1f - 0.04f * exit.value
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        // Solid brand ground — matches the system splash exactly.
        Canvas(Modifier.fillMaxSize()) { drawRect(SplashBackground) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val maxR = size.minDimension * 0.62f
                    val t = ripple.value
                    if (t > 0f) {
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - t) * 0.35f),
                            radius = size.minDimension * 0.28f + t * maxR,
                            style = Stroke(width = 6f),
                        )
                    }
                }
                Canvas(
                    Modifier
                        .size(112.dp)
                        .scale(0.96f + 0.04f * arcReveal.value),
                ) {
                    drawTapioMark(Color.White, progress = arcReveal.value)
                }
            }

            Text(
                text = "Tapio",
                style = WordmarkStyle,
                color = Color.White.copy(alpha = wordmark.value),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .height(56.dp)
                    .alpha(wordmark.value)
                    .graphicsLayer { translationY = (1f - wordmark.value) * 18f },
            )
        }
    }
}
