package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val RoverDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = GunmetalElevated,
    onPrimaryContainer = NeonCyan,
    secondary = NeonLime,
    onSecondary = ObsidianBg,
    secondaryContainer = GunmetalSurface,
    onSecondaryContainer = NeonLime,
    tertiary = NeonViolet,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ChassisDark,
    onSurface = TextPrimary,
    surfaceVariant = GunmetalSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ChassisDark,
    surfaceContainer = GunmetalSurface,
    surfaceContainerHigh = GunmetalElevated,
    surfaceContainerHighest = TitaniumBezel,
    outline = TitaniumBezel,
    outlineVariant = ChromeHighlight,
    error = NeonCrimson,
    onError = TextPrimary,
)

private val RoverShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RoverControlTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = RoverDarkColorScheme,
        typography = RoverTypography,
        shapes = RoverShapes,
        content = content,
    )
}
