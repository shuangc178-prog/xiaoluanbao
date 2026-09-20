package com.example.campuslife.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campuslife.R
import com.example.campuslife.ui.theme.claude

/**
 * 休息卡的卡面。
 *
 * 形象由用户提供（像素画：一只蜷着睡的猫、月亮、云与「Z」），这里只做呈现与动效，
 * 不再自己画造型——美术资产归用户，代码只负责让它出现得好看。
 *
 * 素材两张，都在 `res/drawable-nodpi/`（nodpi 是刻意的：像素画必须原样映射，
 * 不能被密度缩放糊掉，和主题图标、通知图标同一套做法）：
 * - [R.drawable.rest_card_full] 整张卡（橙描边 + 卡名 + 「可跳过 1 次打卡」）；
 * - [R.drawable.rest_card_chip] 只截插画区（卡槽里的小卡面用它，字太小反而糊）。
 *
 * 动效参数（与 [RestCardReveal] 的三段式对齐）：
 * 卡面透明度 0 → 1 用 320ms 线性；缩放 0.86 → 1 用 spring（medium bouncy，轻微回弹）；
 * 图本身额外做一次 1.04 → 1.00 的收束（260ms），让"图从卡里落定"有一下轻微的呼吸。
 */
@Composable
internal fun RestCardArt(
    modifier: Modifier = Modifier,
    revealProgress: Float = 1f,
    glowProgress: Float = 1f,
    full: Boolean = true,
) {
    val accent = MaterialTheme.claude.accent
    val accentSoft = MaterialTheme.claude.accentSoft
    val glow = glowProgress.coerceIn(0f, 1f)
    val reveal = revealProgress.coerceIn(0f, 1f)
    val settle = remember { Animatable(1.04f) }

    LaunchedEffect(reveal) {
        if (reveal >= 1f) {
            settle.animateTo(1f, tween(durationMillis = 260, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 背后柔光：两层径向渐变，不用模糊（Compose 里模糊代价高，渐变足够）。
        // 它让卡面在暗色遮罩上"浮"起来，而不是贴一张纸。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentSoft.copy(alpha = 0.75f * glow),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Image(
            painter = painterResource(
                if (full) R.drawable.rest_card_full else R.drawable.rest_card_chip,
            ),
            // 内容描述为空：卡名与用途在旁边的文字里已经说清楚了，
            // 再念一遍"休息卡图片"对读屏用户只是噪音。
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (full) CardFullAspectRatio else CardChipAspectRatio)
                .scale(settle.value)
                .alpha(0.35f + 0.65f * reveal),
            contentScale = ContentScale.Fit,
        )
    }
}

/** 素材自带的宽高比：整卡 930×1330，插画区 930×732。用常量写死，避免读图后再测一遍。 */
private const val CardFullAspectRatio = 930f / 1330f
private const val CardChipAspectRatio = 930f / 732f

/**
 * 卡槽里的小卡面：直接用插画区截图，圆角与主题一致。
 *
 * 不额外加背景色：素材自带米底与橙描边，再加一层底色会把描边糊掉。
 * [revealProgress] 小于 1 时整体降透明——用于"已用过"的卡（淡一点，但不至于看不出）。
 */
@Composable
internal fun RestCardChip(
    modifier: Modifier = Modifier,
    width: Dp = 52.dp,
    revealProgress: Float = 1f,
) {
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(CardChipAspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .graphicsLayer { alpha = 0.45f + 0.55f * revealProgress.coerceIn(0f, 1f) },
    ) {
        Image(
            painter = painterResource(R.drawable.rest_card_chip),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            // 素材边角自带浅色毛边，裁掉最外一圈让圆角看起来干净。
            alignment = Alignment.Center,
        )
    }
}
