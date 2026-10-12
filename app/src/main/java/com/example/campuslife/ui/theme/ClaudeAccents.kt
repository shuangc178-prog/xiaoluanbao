package com.example.campuslife.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 的语义色角色覆盖不到的那几个 Claude 色（打卡状态、分类标签、图表系列）。
 *
 * 这些色在明暗两套主题下取值不同，所以不能让组件直接引用 Color.kt 里的顶层常量，
 * 而是通过 CompositionLocal 注入，组件统一写 `MaterialTheme.claude.green`。
 */
@Immutable
data class ClaudeAccents(
    val canvas: Color,
    val panel: Color,
    val subtle: Color,
    val strong: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val blue: Color,
    val blueSoft: Color,
    val violet: Color,
    val violetSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val danger: Color,
    /** 背景插画按明暗取不同的图。 */
    val isDark: Boolean,
)

private val LightAccents = ClaudeAccents(
    canvas = ClaudeCanvas,
    panel = ClaudePanel,
    subtle = ClaudeSubtle,
    strong = ClaudePressed,
    accent = ClaudeAccent,
    accentStrong = ClaudeAccentStrong,
    accentSoft = ClaudeAccentSoft,
    green = ClaudeGreen,
    greenSoft = ClaudeGreenSoft,
    blue = ClaudeBlue,
    blueSoft = ClaudeBlueSoft,
    violet = ClaudeViolet,
    violetSoft = ClaudeVioletSoft,
    amber = ClaudeAmber,
    amberSoft = ClaudeAmberSoft,
    danger = ClaudeDanger,
    isDark = false,
)

private val DarkAccents = ClaudeAccents(
    canvas = ClaudeDarkCanvas,
    panel = ClaudeDarkPanel,
    subtle = ClaudeDarkSubtle,
    strong = ClaudeDarkPressed,
    accent = ClaudeDarkAccent,
    accentStrong = ClaudeDarkAccentStrong,
    accentSoft = ClaudeDarkAccentSoft,
    green = ClaudeDarkGreen,
    greenSoft = ClaudeDarkGreenSoft,
    blue = ClaudeDarkBlue,
    blueSoft = ClaudeDarkBlueSoft,
    violet = ClaudeDarkViolet,
    violetSoft = ClaudeDarkVioletSoft,
    amber = ClaudeDarkAmber,
    amberSoft = ClaudeDarkAmberSoft,
    danger = ClaudeDarkDanger,
    isDark = true,
)

internal val LocalClaudeAccents = staticCompositionLocalOf { LightAccents }

val MaterialTheme.claude: ClaudeAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalClaudeAccents.current

@Composable
internal fun ProvideClaudeAccents(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalClaudeAccents provides if (darkTheme) DarkAccents else LightAccents,
        content = content,
    )
}
