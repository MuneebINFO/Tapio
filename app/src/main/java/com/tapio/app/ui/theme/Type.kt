package com.tapio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tuned on the platform font: tighter, heavier display/headline styles
 * for a confident, app-store-ready feel, calmer body text.
 */
private val default = Typography()

val TapioTypography = Typography(
    displaySmall = default.displaySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = default.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = default.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = default.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = default.bodyLarge.copy(lineHeight = 24.sp),
)

/** Wordmark style — used by [com.tapio.app.ui.brand.TapioLogo]. */
val WordmarkStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = (-1).sp,
)
