package com.tapio.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Cross-fades (with a subtle scale) between the discrete states of a screen, so
 * moving from "waiting" to "transferring" to "done" feels like one continuous
 * surface rather than a hard cut.
 */
@Composable
fun <T> AnimatedStatus(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f))
        },
        label = "AnimatedStatus",
    ) { state ->
        content(state)
    }
}
