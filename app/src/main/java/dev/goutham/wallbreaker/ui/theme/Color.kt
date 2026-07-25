package dev.goutham.wallbreaker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Brick & Paper" — a Material 3 scheme seeded from Wallbreaker's coral
 * (#E8503A). Three deliberate deviations from a stock generation:
 *   1. `error` is a cooler crimson, so a failed sync can never be mistaken for
 *      the brand coral (failed-sync is now a first-class state).
 *   2. Dark `primary` stays a hot coral (not a pastel salmon) — the biggest
 *      "not a template" signal in dark mode.
 *   3. `tertiary` is amber and reserved EXCLUSIVELY for "unlocked / full text".
 * Dynamic color is intentionally OFF (see Theme.kt) — the coral is the brand.
 */

// The signature mark colour (wall + bolt). Not a scheme role; used by WallMark.
val Coral = Color(0xFFE8503A)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB02F23),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3E0400),
    secondary = Color(0xFF775650),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFA62339),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9DC),
    onErrorContainer = Color(0xFF400012),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DDD7),
    onSurfaceVariant = Color(0xFF534340),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFEF1ED),
    surfaceContainer = Color(0xFFF8EBE7),
    surfaceContainerHigh = Color(0xFFF2E5E1),
    surfaceContainerHighest = Color(0xFFECDFDB),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BD),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    inversePrimary = Color(0xFFFFB4A4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8D77),
    onPrimary = Color(0xFF561F12),
    primaryContainer = Color(0xFF7A2717),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB5),
    onSecondary = Color(0xFF442A25),
    secondaryContainer = Color(0xFF5D3F3A),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB1BC),
    onError = Color(0xFF5F112A),
    errorContainer = Color(0xFF7E2039),
    onErrorContainer = Color(0xFFFFD9DC),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF534340),
    onSurfaceVariant = Color(0xFFD8C2BD),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF221816),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322F),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF534340),
    inverseSurface = Color(0xFFF1DFDA),
    inverseOnSurface = Color(0xFF392E2B),
    inversePrimary = Color(0xFFB02F23),
)

val WallbreakerLightColors = LightColors
val WallbreakerDarkColors = DarkColors
