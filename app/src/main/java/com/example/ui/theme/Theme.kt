package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

private val SimmerLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = TerracottaOnPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaOnContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    tertiary = SaffronTertiary,
    onTertiary = SaffronOnTertiary,
    tertiaryContainer = SaffronContainer,
    onTertiaryContainer = SaffronOnContainer,
    background = ParchmentBackground,
    onBackground = EspressoOnBackground,
    surface = CreamSurface,
    onSurface = EspressoOnBackground,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = WarmTaupeOnSurfaceVariant,
    surfaceContainerLowest = CardSurfaceLight,
    surfaceContainerLow = CreamSurface,
    surfaceContainer = CreamSurfaceVariant,
    surfaceContainerHigh = ColorTokens.LightContainerHigh,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val SimmerDarkColorScheme = darkColorScheme(
    primary = TerracottaDarkPrimary,
    onPrimary = TerracottaDarkOnPrimary,
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = TerracottaDarkOnContainer,
    secondary = SageDarkSecondary,
    onSecondary = SageDarkOnSecondary,
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = SageDarkOnContainer,
    tertiary = SaffronDarkTertiary,
    onTertiary = SaffronDarkOnTertiary,
    tertiaryContainer = SaffronDarkContainer,
    onTertiaryContainer = SaffronDarkOnContainer,
    background = EspressoDarkBackground,
    onBackground = LinenOnDarkBackground,
    surface = EspressoDarkSurface,
    onSurface = LinenOnDarkBackground,
    surfaceVariant = EspressoDarkSurfaceHigh,
    onSurfaceVariant = WarmGrayOnDarkVariant,
    surfaceContainerLowest = EspressoDarkBackground,
    surfaceContainerLow = EspressoDarkSurfaceContainer,
    surfaceContainer = EspressoDarkSurfaceContainer,
    surfaceContainerHigh = EspressoDarkSurfaceHigh,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

private object ColorTokens {
    val LightContainerHigh = androidx.compose.ui.graphics.Color(0xFFEBDCD0)
}

val SimmerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SimmerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) SimmerDarkColorScheme else SimmerLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SimmerTypography,
        shapes = SimmerShapes,
        content = content
    )
}
