package com.tapio.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.app.ui.theme.WordmarkStyle

/**
 * The Tapio mark: a contact point sending three arcs of signal. Single source of
 * truth for the in-app logo; the launcher icon mirrors it as a vector drawable.
 *
 * @param progress `0f..1f` reveals the arcs one by one — drive it for the intro.
 */
@Composable
fun TapioMark(
    modifier: Modifier = Modifier.size(96.dp),
    color: Color = LocalContentColor.current,
    progress: Float = 1f,
) {
    Canvas(modifier = modifier) {
        drawTapioMark(color, progress)
    }
}

/** The mark plus the "Tapio" wordmark, laid out horizontally. */
@Composable
fun TapioLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TapioMark(modifier = Modifier.size(40.dp), color = color)
        Spacer(Modifier.width(12.dp))
        Text(text = "Tapio", style = WordmarkStyle, color = color)
    }
}

/** Draws the mark into [this] DrawScope, filling its size. */
fun DrawScope.drawTapioMark(color: Color, progress: Float = 1f) {
    val unit = size.minDimension / 108f
    val center = Offset(size.width / 2f, size.height / 2f)
    // Mark art spans roughly x∈[29,80] in a 108 grid; recentre on the canvas.
    val markCenter = Offset(center.x - 18f * unit, center.y)

    drawCircle(color = color, radius = 6.5f * unit, center = markCenter)

    val strokePx = 7f * unit
    val arcRadii = floatArrayOf(14f, 21f, 28f)
    arcRadii.forEachIndexed { index, r ->
        val reveal = ((progress - index * 0.22f) / 0.34f).coerceIn(0f, 1f)
        if (reveal <= 0f) return@forEachIndexed
        val radius = r * unit
        drawArc(
            color = color.copy(alpha = color.alpha * reveal),
            startAngle = -55f,
            sweepAngle = 110f * reveal,
            useCenter = false,
            topLeft = Offset(markCenter.x - radius, markCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14171F)
@Composable
private fun TapioLogoPreview() {
    TapioTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapioMark(color = Color.White)
            TapioLogo(color = Color.White)
        }
    }
}
