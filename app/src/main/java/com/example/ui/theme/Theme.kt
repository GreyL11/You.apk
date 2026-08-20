package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = CobaltDark,
    onPrimary = OnCobaltDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = TealDark,
    onSecondary = OnTealDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = SandDark,
    onTertiary = OnSandDark,
    tertiaryContainer = SandContainerDark,
    onTertiaryContainer = OnSandContainerDark,
    error = RedDark,
    onError = OnRedDark,
    errorContainer = RedContainerDark,
    onErrorContainer = OnRedContainerDark,
    background = InkBackground,
    onBackground = InkOnSurface,
    surface = InkSurface,
    onSurface = InkOnSurface,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    inverseSurface = InkOnSurface,
    inverseOnSurface = InkBackground,
)

private val LightColors = lightColorScheme(
    primary = CobaltLight,
    onPrimary = OnCobaltLight,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = OnCobaltContainerLight,
    secondary = TealLight,
    onSecondary = OnTealLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = SandLight,
    onTertiary = OnSandLight,
    tertiaryContainer = SandContainerLight,
    onTertiaryContainer = OnSandContainerLight,
    error = RedLight,
    onError = OnRedLight,
    errorContainer = RedContainerLight,
    onErrorContainer = OnRedContainerLight,
    background = PaperBackground,
    onBackground = PaperOnSurface,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    inverseSurface = PaperOnSurface,
    inverseOnSurface = PaperSurface,
)

/**
 * The app theme. Follows the system's dark setting, which the previous version took as a parameter
 * and then ignored — it hardcoded the light scheme, so a phone in dark mode got a white glare at
 * 6am, which is exactly when someone logs a sleep.
 *
 * Dynamic (wallpaper) colour is deliberately not offered. The accent here carries meaning — cobalt
 * is "act on this" and teal is "nothing needed" — and a palette that changes per phone would recolour
 * that meaning into whatever the user's wallpaper happens to be.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Edge-to-edge draws behind the status bar, so the bar's own icons have to be told which way to
    // go. Without this they stay dark and vanish against the ink ground in dark mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
