package com.personal.jizhang.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF147D73), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F2E9), onPrimaryContainer = Color(0xFF074B43),
    secondary = Color(0xFF526F67), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEE8), onSecondaryContainer = Color(0xFF344E47),
    tertiary = Color(0xFFA16A31),
    background = Color(0xFFF6F7F5), onBackground = Color(0xFF172B29),
    surface = Color.White, onSurface = Color(0xFF172B29),
    surfaceVariant = Color(0xFFEBF0EB), onSurfaceVariant = Color(0xFF5C6F6B),
    surfaceContainer = Color(0xFFEEF2EE),
    surfaceContainerLow = Color(0xFFF2F5F1),
    surfaceContainerHigh = Color(0xFFE6ECE6),
    outline = Color(0xFF7B8D86), outlineVariant = Color(0xFFDCE5DD),
    error = Color(0xFFB54727), onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85D5C4), onPrimary = Color(0xFF003A33),
    primaryContainer = Color(0xFF18574C), onPrimaryContainer = Color(0xFFBBF0DF),
    secondary = Color(0xFFACCBBE), onSecondary = Color(0xFF1A362C),
    secondaryContainer = Color(0xFF314D41), onSecondaryContainer = Color(0xFFCEE9D9),
    tertiary = Color(0xFFE6BB82),
    background = Color(0xFF111B19), onBackground = Color(0xFFE1EBE5),
    surface = Color(0xFF1A2622), onSurface = Color(0xFFE1EBE5),
    surfaceVariant = Color(0xFF2C3A34), onSurfaceVariant = Color(0xFFB3C5BA),
    surfaceContainer = Color(0xFF202D27),
    surfaceContainerLow = Color(0xFF19251F),
    surfaceContainerHigh = Color(0xFF2A3830),
    outline = Color(0xFF8B9F91), outlineVariant = Color(0xFF3F5145),
    error = Color(0xFFFFB59D), onError = Color(0xFF5B1A04),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun JizhangTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}
