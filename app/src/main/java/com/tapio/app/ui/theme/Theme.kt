package com.tapio.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TapioDark = darkColorScheme(
    primary = Color(0xFF9EC2FF),
    secondary = Color(0xFF7FD1C1),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
)

private val TapioLight = lightColorScheme(
    primary = Color(0xFF2A5BD7),
    secondary = Color(0xFF1E7F6E),
)

/**
 * Tapio's Material 3 theme. Dark by default but follows the system setting, and
 * uses Material You dynamic colour on Android 12+.
 */
@Composable
fun TapioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> TapioDark
        else -> TapioLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
