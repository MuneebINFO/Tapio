package com.tapio.app.ui.components

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** "1,8 Mo", "823 Ko" — a compact, French-formatted byte count. */
fun formatBytes(bytes: Long?): String = when {
    bytes == null || bytes < 0 -> "—"
    bytes < UNIT -> "$bytes o"
    else -> {
        val exponent = (ln(bytes.toDouble()) / ln(UNIT.toDouble())).toInt().coerceIn(1, UNITS.size)
        val value = bytes / UNIT.toDouble().pow(exponent)
        String.format(Locale.FRANCE, "%.1f %s", value, UNITS[exponent - 1])
    }
}

private const val UNIT = 1024L
private val UNITS = listOf("Ko", "Mo", "Go", "To")
