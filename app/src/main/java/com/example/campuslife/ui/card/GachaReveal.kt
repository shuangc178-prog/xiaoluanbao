package com.example.campuslife.ui.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.campuslife.ui.components.ClaudeButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.GachaCard
import kotlinx.coroutines.delay

/**
 * 抽到一张卡时的开卡动效。
 *
 * 画面本身在 [CardStage]（统计页「看卡」用的是同一屏，只是不播动画），
 * 这里只负责四拍节奏——沿用休息卡那套克制的做法，把柔光换成**这张卡的稀有度色**，
 * 让档次靠颜色和文字体现，不靠粒子、闪光或音效：
 *
 * | 阶段 | 时长 | 内容 |
 * |---|---|---|
 * | ① 落暗 | 260ms | 背景压到 62% 黑 |
 * | ② 浮现 | 180ms 起，320ms + spring | 卡面淡入并弹性放大到 100% |
 * | ③ 成形 | 300ms 起，520ms | 稀有度柔光从卡后长出来 |
 * | ④ 呼吸 | 420ms 起，520ms + 880ms | 柔光呼吸一次（1 → 0.55）后静止 |
 *
 * 出口有三个：点「收下」、点背景、按返回键，不逼用户等动画放完。
 */
@Composable
internal fun GachaReveal(
    card: GachaCard,
    owned: Int,
    maxCopies: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rarityAccent(card.rarity)
    val soft = raritySoft(card.rarity)

    val scrim = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.86f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scrim.animateTo(0.62f, tween(durationMillis = 260, easing = LinearOutSlowInEasing))
    }
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
    LaunchedEffect(Unit) {
        delay(300)
        glow.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        glow.animateTo(0.55f, tween(durationMillis = 880, easing = LinearOutSlowInEasing))
    }

    CardStage(
        card = card,
        onDismiss = onDismiss,
        modifier = modifier,
        scrimAlpha = scrim.value,
        cardAlpha = cardAlpha.value,
        cardScale = cardScale.value,
        glow = glow.value,
    ) {
        Text(
            text = card.series.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = card.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = soft,
            contentColor = accent,
        ) {
            Text(
                // 只报状态（稀有度 + 已有几张），不解释玩法。
                text = if (owned >= maxCopies) {
                    "${card.rarity.label} · $owned/$maxCopies 已满"
                } else {
                    "${card.rarity.label} · $owned/$maxCopies"
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        ClaudeButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("收下")
        }
    }
}
