package com.example.campuslife.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 获得休息卡时的动效。
 *
 * 参照常见的「得卡」三段式，但按这个 App 的克制基调做了减法：
 *
 * | 阶段 | 时长 | 内容 |
 * |---|---|---|
 * | ① 落暗 | 260ms | 背景压到 62% 黑，卡面此时还小且透明 |
 * | ② 浮现 | 320ms + spring | 卡面淡入，并弹性放大到 100%（medium bouncy，轻微回弹） |
 * | ③ 成形 | 520ms | 月与云、浮尘从卡里长出来（图形比卡面晚约 300ms 起） |
 * | ④ 柔光 | 520ms + 880ms | 背后柔光呼吸一次（0.35 → 1 → 0.55）后静止 |
 *
 * 没有粒子雨、没有烟花、没有音效：一次安静的「给你一张卡」。
 * 退出方式有三个——点「收下」、点背景、按返回，不逼用户等动画放完。
 */
@Composable
internal fun RestCardReveal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrim = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.86f) }
    val cardAlpha = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val glow = remember { Animatable(0.35f) }

    // ① 落暗：先把背景压下去。
    LaunchedEffect(Unit) {
        scrim.animateTo(0.62f, tween(durationMillis = 260, easing = LinearOutSlowInEasing))
    }
    // ② 浮现：等背景暗下来一点再出现，避免卡和背景一起冒出来显得吵。
    LaunchedEffect(Unit) {
        delay(180)
        cardAlpha.animateTo(1f, tween(durationMillis = 320, easing = LinearEasing))
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    // ③ 成形：图形比卡面稍晚，形成「图从卡里长出来」的顺序感。
    LaunchedEffect(Unit) {
        delay(300)
        reveal.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }
    // ④ 柔光呼吸一次就停：循环闪光会让页面一直「吵」。
    LaunchedEffect(Unit) {
        delay(420)
        glow.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        glow.animateTo(0.55f, tween(durationMillis = 880, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .alpha(cardAlpha.value)
                .scale(cardScale.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 卡面直接用用户提供的整卡素材：卡名与「可跳过 1 次打卡」都画在图里，
            // 这里不再复述一遍，只在下面补上"怎么用"的一句话。
            // 只约束宽度、高度由素材宽高比推出来——同时写死宽高会把卡面压变形。
            RestCardArt(
                modifier = Modifier.fillMaxWidth(0.78f),
                revealProgress = reveal.value,
                glowProgress = glow.value,
            )

            Text(
                text = "获得一张休息卡",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("收下")
            }
        }
    }
}
