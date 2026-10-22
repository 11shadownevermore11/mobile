package ru.kodnovorossii.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Цвета знака ФСП ДНР: красный, синий, угольный. */
object Brand {
    val Red = Color(0xFFE3263A)
    val Blue = Color(0xFF1B4FA3)
    val Ink = Color(0xFF17191F)
    val Ok = Color(0xFF10805A)
    val Warn = Color(0xFFB26A00)
    val Star = Color(0xFFF2A900)
}

val LocalDark = staticCompositionLocalOf { false }

private val Light = lightColorScheme(
    primary = Brand.Red, onPrimary = Color.White, secondary = Brand.Blue, onSecondary = Color.White,
    primaryContainer = Color(0xFFFFDADF), onPrimaryContainer = Color(0xFF5C0011),
    background = Color(0xFFEEF1F6), onBackground = Brand.Ink, surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE6EAF1), onSurfaceVariant = Color(0xFF3E4759), outline = Color(0xFFC9D1DE), outlineVariant = Color(0xFFDCE2EB),
    secondaryContainer = Color(0xFFDDE6F6), onSecondaryContainer = Brand.Ink, tertiary = Brand.Ink,
    // Слои глубины: фон → карточки → вложенные элементы, чтобы ничего не сливалось
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FC), surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
)
private val Dark = darkColorScheme(
    // «Ночной Донбасс», как на сайте: золотой акцент огней на тёмно-синем
    primary = Color(0xFFF5C451), onPrimary = Color(0xFF1A1405), secondary = Color(0xFF7FB2FF), onSecondary = Color(0xFF071324),
    primaryContainer = Color(0xFF3A3010), onPrimaryContainer = Color(0xFFFFE6A3),
    background = Color(0xFF071324), onBackground = Color(0xFFF2F6FC), surface = Color(0xFF0C1C33), onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF1C3657), onSurfaceVariant = Color(0xFFC6D3E4), outline = Color(0xFF3A5A82), outlineVariant = Color(0xFF2B4A70),
    secondaryContainer = Color(0xFF2B4A70), onSecondaryContainer = Color(0xFFF2F6FC), tertiary = Color(0xFFFF6B7D),
    // Слои глубины, как на сайте («Ночной Донбасс»): фон темнее, карточки заметно светлее
    surfaceContainerLowest = Color(0xFF071324), surfaceContainerLow = Color(0xFF0E2039), surfaceContainer = Color(0xFF132A46),
    surfaceContainerHigh = Color(0xFF17304F), surfaceContainerHighest = Color(0xFF1C3657),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val Shapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun KodTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shapes, content = content)
    }
}
