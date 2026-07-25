package dev.goutham.wallbreaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app theme. Dynamic (Material You) colour is deliberately NOT used — a brand
 * this small lives or dies on its coral accent, and wallpaper theming would
 * erase it. Only light/dark switch.
 */
@Composable
fun WallbreakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WallbreakerDarkColors else WallbreakerLightColors,
        typography = WallbreakerTypography,
        content = content,
    )
}
