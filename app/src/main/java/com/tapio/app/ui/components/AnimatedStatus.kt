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
 *
 * The transition is keyed on the *kind* of state, not its value. A progressing
 * transfer emits a new state several times a second; without this each percentage
 * would cross-fade against the previous one and the screen would strobe. States of
 * the same kind now just recompose in place, and only a real stage change animates.
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
        contentKey = { state -> state?.let { it::class } ?: Unit },
        label = "AnimatedStatus",
    ) { state ->
        content(state)
    }
}
