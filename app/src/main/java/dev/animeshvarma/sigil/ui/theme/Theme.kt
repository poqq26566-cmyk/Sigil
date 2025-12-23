package dev.animeshvarma.sigil.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun SigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 1. Material You (Dynamic Colors) - Priority if enabled
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 2. Custom Seed Color - "Standardized" Propagation
        seedColor != null -> {
            val seed = Color(seedColor)

            if (darkTheme) {
                darkColorScheme(
                    // Main Accents
                    primary = seed,
                    onPrimary = Color.Black, // High contrast on the colored button
                    primaryContainer = seed.copy(alpha = 0.3f),
                    onPrimaryContainer = seed, // Text inside container matches seed

                    secondary = seed,
                    onSecondary = Color.Black,
                    secondaryContainer = seed.copy(alpha = 0.2f),
                    onSecondaryContainer = seed,

                    tertiary = seed,
                    onTertiary = Color.Black,
                    tertiaryContainer = seed.copy(alpha = 0.2f),
                    onTertiaryContainer = seed,

                    // Backgrounds
                    background = Color(0xFF121212), // Deep black-grey
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurface = Color.White,
                    onSurfaceVariant = Color.LightGray,
                    surfaceContainer = Color(0xFF1E1E1E),
                    surfaceContainerLow = Color(0xFF1A1A1A),
                    surfaceContainerHigh = Color(0xFF252525),

                    // Borders
                    outline = seed.copy(alpha = 0.6f),
                    outlineVariant = seed.copy(alpha = 0.3f)
                )
            } else {
                lightColorScheme(
                    primary = seed,
                    onPrimary = Color.White,
                    primaryContainer = seed.copy(alpha = 0.2f),
                    onPrimaryContainer = Color.Black, // Dark text for contrast on light container

                    secondary = seed,
                    onSecondary = Color.White,
                    secondaryContainer = seed.copy(alpha = 0.1f),
                    onSecondaryContainer = Color.Black,

                    tertiary = seed,
                    onTertiary = Color.White,
                    tertiaryContainer = seed.copy(alpha = 0.1f),
                    onTertiaryContainer = Color.Black,

                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color.Black,

                    outline = seed.copy(alpha = 0.5f)
                )
            }
        }

        // 3. Fallback Defaults
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}