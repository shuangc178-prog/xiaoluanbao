package com.example.campuslife.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ClaudeAccent,
    onPrimary = Color.White,
    primaryContainer = ClaudeAccentSoft,
    onPrimaryContainer = ClaudeAccentStrong,
    secondary = ClaudeTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = ClaudeSubtle,
    onSecondaryContainer = ClaudeText,
    tertiary = ClaudeAmber,
    onTertiary = Color.White,
    tertiaryContainer = ClaudeAmberSoft,
    onTertiaryContainer = ClaudeAmber,
    background = ClaudeCanvas,
    onBackground = ClaudeText,
    surface = ClaudePanel,
    onSurface = ClaudeText,
    surfaceVariant = ClaudeSubtle,
    onSurfaceVariant = ClaudeMuted,
    surfaceContainerLowest = ClaudePanel,
    surfaceContainerLow = ClaudeSidebar,
    surfaceContainer = ClaudeSidebar,
    surfaceContainerHigh = ClaudeSubtle,
    surfaceContainerHighest = ClaudePressed,
    surfaceTint = ClaudeAccent,
    outline = ClaudeBorderStrong,
    outlineVariant = ClaudeBorder,
    error = ClaudeDanger,
    onError = Color.White,
    errorContainer = ClaudeDangerSoft,
    onErrorContainer = ClaudeDanger,
)

private val DarkColors = darkColorScheme(
    primary = ClaudeDarkAccent,
    onPrimary = ClaudeText,
    primaryContainer = ClaudeDarkAccentSoft,
    onPrimaryContainer = ClaudeDarkAccentStrong,
    secondary = ClaudeDarkTextSecondary,
    onSecondary = ClaudeCanvas,
    secondaryContainer = ClaudeDarkSubtle,
    onSecondaryContainer = ClaudeDarkText,
    tertiary = ClaudeDarkAmber,
    onTertiary = ClaudeCanvas,
    tertiaryContainer = ClaudeDarkAmberSoft,
    onTertiaryContainer = ClaudeDarkAmber,
    background = ClaudeDarkCanvas,
    onBackground = ClaudeDarkText,
    surface = ClaudeDarkPanel,
    onSurface = ClaudeDarkText,
    surfaceVariant = ClaudeDarkSubtle,
    onSurfaceVariant = ClaudeDarkMuted,
    surfaceContainerLowest = ClaudeDarkSidebar,
    surfaceContainerLow = ClaudeDarkSidebar,
    surfaceContainer = ClaudeDarkPanel,
    surfaceContainerHigh = ClaudeDarkPressed,
    surfaceContainerHighest = ClaudeDarkPressed,
    surfaceTint = ClaudeDarkAccent,
    outline = ClaudeDarkBorderStrong,
    outlineVariant = ClaudeDarkBorder,
    error = ClaudeDarkDanger,
    onError = ClaudeCanvas,
    errorContainer = ClaudeDarkDangerSoft,
    onErrorContainer = ClaudeDarkDanger,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ProvideClaudeAccents(darkTheme = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ClaudeTypography,
        ) {
            // 全局按压手感：普通 clickable 换成暖光 + 微缩回弹，Material 组件的水波加深一点。
            val glow = MaterialTheme.claude.accent
            CompositionLocalProvider(
                LocalIndication provides remember(glow) { ClaudePressIndication(glow) },
                LocalRippleConfiguration provides ClaudeRippleConfiguration,
                content = content,
            )
        }
    }
}
