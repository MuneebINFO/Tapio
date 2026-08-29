package com.tapio.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Tapio's brand colours. The mark is indigo→violet; particles pick up the cyan. */
val BrandIndigo = Color(0xFF5A6CF0)
val BrandIndigoDeep = Color(0xFF3A47C4)
val BrandCyan = Color(0xFF22D3EE)

internal val TapioDarkColors = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF0B1030),
    primaryContainer = Color(0xFF2C317A),
    onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF00363F),
    tertiary = Color(0xFFB9C3FF),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE6E8EF),
    surface = Color(0xFF14171F),
    onSurface = Color(0xFFE6E8EF),
    surfaceVariant = Color(0xFF262A36),
    onSurfaceVariant = Color(0xFF9BA1B0),
    outline = Color(0xFF3A3F4D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
)

internal val TapioLightColors = lightColorScheme(
    primary = Color(0xFF4A56D8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E3FF),
    onPrimaryContainer = Color(0xFF14186B),
    secondary = Color(0xFF0E8BA8),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4655D8),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12141C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12141C),
    surfaceVariant = Color(0xFFEDEFF6),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFFC7CAD6),
    error = Color(0xFFD92D2D),
    onError = Color(0xFFFFFFFF),
)
