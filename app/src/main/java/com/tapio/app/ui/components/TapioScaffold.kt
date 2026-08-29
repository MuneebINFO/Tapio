package com.tapio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tapio.app.R

/**
 * Standard screen chrome: the [AmbientBackground], a slim transparent top bar with
 * an optional back button and title, then [content] with comfortable side padding.
 */
@Composable
fun TapioScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
    content: @Composable (Modifier) -> Unit,
) {
    AmbientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            TopBar(title = title, onBack = onBack)
            content(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun TopBar(title: String?, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val label = stringResource(R.string.action_back)
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = label }) {
                ChevronLeft()
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = if (onBack != null) 4.dp else 12.dp),
            )
        }
    }
}

@Composable
private fun ChevronLeft() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val cap = StrokeCap.Round
        drawLine(color, Offset(w * 0.62f, h * 0.12f), Offset(w * 0.20f, h * 0.5f), strokeWidth = 6f, cap = cap)
        drawLine(color, Offset(w * 0.20f, h * 0.5f), Offset(w * 0.62f, h * 0.88f), strokeWidth = 6f, cap = cap)
    }
}
