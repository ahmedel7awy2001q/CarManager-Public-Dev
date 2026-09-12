package com.ahmed.carmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.carmanager.data.settings.AccentPalette
import com.ahmed.carmanager.data.settings.AppThemeMode

/** Compact premium automotive visual system: quiet pearl surfaces + precise accent colour. */
private data class AccentSet(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color
)

private fun accentSet(value: AccentPalette): AccentSet = when (value) {
    AccentPalette.OCEAN -> AccentSet(
        Color(0xFF007F90), Color(0xFFDDF5F6), Color(0xFF073F47),
        Color(0xFF28786E), Color(0xFFE1F2ED),
        Color(0xFF65D9E2), Color(0xFF153A41), Color(0xFF6AD8C9), Color(0xFF143B37)
    )
    AccentPalette.EMERALD -> AccentSet(
        Color(0xFF1C7452), Color(0xFFE0F3E9), Color(0xFF0D3D2A),
        Color(0xFF176B65), Color(0xFFE0F0EE),
        Color(0xFF70D7A7), Color(0xFF173C2F), Color(0xFF76D8CD), Color(0xFF153B38)
    )
    AccentPalette.CRIMSON -> AccentSet(
        Color(0xFF9B343B), Color(0xFFF7E7E8), Color(0xFF57191F),
        Color(0xFF864052), Color(0xFFF3E8EB),
        Color(0xFFFFA4A7), Color(0xFF51272B), Color(0xFFFFAEC0), Color(0xFF4A2933)
    )
    AccentPalette.VIOLET -> AccentSet(
        Color(0xFF5E50A0), Color(0xFFECEAF8), Color(0xFF342463),
        Color(0xFF6D5192), Color(0xFFF0EAF5),
        Color(0xFFC8BBFF), Color(0xFF3B325D), Color(0xFFD8B5F4), Color(0xFF44324F)
    )
    AccentPalette.AMBER -> AccentSet(
        Color(0xFF8F601D), Color(0xFFF8EEDA), Color(0xFF4C3209),
        Color(0xFF8C5B36), Color(0xFFF6E9DF),
        Color(0xFFF1C675), Color(0xFF49381E), Color(0xFFF1B68F), Color(0xFF473228)
    )
    AccentPalette.GRAPHITE -> AccentSet(
        Color(0xFF405965), Color(0xFFE6ECEF), Color(0xFF23323A),
        Color(0xFF526870), Color(0xFFE7ECEE),
        Color(0xFFBCD0D9), Color(0xFF2B3A42), Color(0xFFB7CBD2), Color(0xFF2D3E44)
    )
}

private fun lightColors(accent: AccentPalette) = accentSet(accent).let { a ->
    lightColorScheme(
        primary = a.primary,
        onPrimary = Color.White,
        primaryContainer = a.primaryContainer,
        onPrimaryContainer = a.onPrimaryContainer,
        secondary = a.secondary,
        onSecondary = Color.White,
        secondaryContainer = a.secondaryContainer,
        onSecondaryContainer = Color(0xFF143B37),
        tertiary = Color(0xFF526873),
        tertiaryContainer = Color(0xFFE8EEF1),
        onTertiaryContainer = Color(0xFF24343D),
        background = Color(0xFFF4F7F8),
        surface = Color(0xFFFCFDFD),
        surfaceVariant = Color(0xFFEEF3F4),
        onSurface = Color(0xFF11171A),
        onSurfaceVariant = Color(0xFF626C71),
        outline = Color(0xFFCAD3D6),
        outlineVariant = Color(0xFFE1E7E9),
        error = Color(0xFFB3261E),
        errorContainer = Color(0xFFFBE8E6),
        onErrorContainer = Color(0xFF681B16)
    )
}

private fun darkColors(accent: AccentPalette) = accentSet(accent).let { a ->
    darkColorScheme(
        primary = a.darkPrimary,
        onPrimary = Color(0xFF062329),
        primaryContainer = a.darkPrimaryContainer,
        onPrimaryContainer = Color(0xFFE8FAFB),
        secondary = a.darkSecondary,
        onSecondary = Color(0xFF06221F),
        secondaryContainer = a.darkSecondaryContainer,
        onSecondaryContainer = Color(0xFFE7F8F4),
        tertiary = Color(0xFFB9C9D1),
        tertiaryContainer = Color(0xFF283840),
        onTertiaryContainer = Color(0xFFE8F0F3),
        background = Color(0xFF071014),
        surface = Color(0xFF0D171B),
        surfaceVariant = Color(0xFF172328),
        onSurface = Color(0xFFF2F6F7),
        onSurfaceVariant = Color(0xFFB5C0C4),
        outline = Color(0xFF3A4A50),
        outlineVariant = Color(0xFF25343A),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF5A2927),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

private val CarTypography = Typography(
    headlineLarge = TextStyle(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 12.8.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

private val CarShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CarManagerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.OCEAN,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors(accentPalette) else lightColors(accentPalette),
        typography = CarTypography,
        shapes = CarShapes,
        content = content
    )
}
