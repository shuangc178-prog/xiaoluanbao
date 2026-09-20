package com.example.campuslife.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Claude 主题的排版契约：标题走衬线（对应 CSS 里的 Georgia / Noto Serif SC），
 * UI 与正文走无衬线（对应 system-ui / Microsoft YaHei UI）。
 *
 * CSS 里的字重是 500 / 550 / 650，Material 只支持百位档，
 * 这里统一收敛到 Normal(400) 与 Medium(500)，个别组件再单独加粗。
 */
internal val ClaudeSerif = FontFamily.Serif
internal val ClaudeSans = FontFamily.SansSerif

internal val ClaudeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 47.sp,
        letterSpacing = (-1.3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 29.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = ClaudeSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = ClaudeSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)
