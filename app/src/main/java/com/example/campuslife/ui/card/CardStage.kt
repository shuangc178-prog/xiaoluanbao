package com.example.campuslife.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.GachaCard

/** 卡面占舞台宽度的比例；高度再按屏幕高度收一次，见 [calculateCardArtSize]。 */
private const val CARD_WIDTH_FRACTION = 0.76f

/** 舞台左右留白：卡面宽度百分比是照着「扣掉它之后」的宽度算的。 */
private val StageHorizontalPadding = 28.dp

/** 卡面之外还要留的竖向空间：底下那行文字 / 按钮。 */
private val StageFooterReserve = 116.dp

/**
 * 一整屏展示一张卡：压暗的背景 + 稀有度柔光 + 卡面 + 底下的信息。
 *
 * 抽卡「开卡」与统计页「看卡」共用它，两边画面因此天然一致——
 * **看卡就是开卡那一屏停下来、不播放动画的样子**，不再是另一套排版。
 *
 * 动画由调用方提供：把 [cardAlpha] / [cardScale] / [glow] 喂进来即可；
 * 不播动画时全部用默认值（不透明、100%、柔光常亮）。
 */
@Composable
internal fun CardStage(
    card: GachaCard,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 已有几张。0 = 还没到手，卡面按收集格子那套处理成灰底剪影。 */
    owned: Int = 1,
    scrimAlpha: Float = 0.62f,
    cardAlpha: Float = 1f,
    cardScale: Float = 1f,
    glow: Float = 0.55f,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 在根上量一次整屏：卡面尺寸必须由「屏幕有多大」算出来。
    // 放到下面 Column 里再量是量不准的——那一层的高度不受约束，会得到无穷大。
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val artSize = calculateCardArtSize(
            availableWidth = maxWidth - StageHorizontalPadding * 2,
            availableHeight = maxHeight,
            aspectRatio = card.aspectRatio,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
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
                    .padding(horizontal = StageHorizontalPadding)
                    .alpha(cardAlpha)
                    .scale(cardScale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CardArtPlinth(card = card, glow = glow, size = artSize, owned = owned)
                content()
            }
        }
    }
}

/**
 * 柔光 + 卡面。
 *
 * 柔光用径向渐变画、不做模糊（Compose 里模糊代价高，渐变足够）。
 * 尺寸由 [CardStage] 算好传进来，卡面只认一个确定的大小，
 * 不靠 `fillMaxWidth` 反推——否则屏幕一矮，卡会顶出屏幕、把底下的字挤没。
 *
 * **还没到手的卡和收集格子里长得一样**：降饱和 + 压暗（都读 [cardArtAlpha] /
 * [Desaturate] 那一份口径）。这里原来直接把原图铺上去，结果格子里是灰的、
 * 点开却是彩色的，等于把「还没到手」这个状态弄丢了。
 */
@Composable
private fun CardArtPlinth(
    card: GachaCard,
    glow: Float,
    size: DpSize,
    owned: Int,
) {
    val soft = raritySoft(card.rarity)
    Box(
        modifier = Modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    soft.copy(alpha = 0.85f * glow),
                    Color.Transparent,
                ),
            ),
        ),
    ) {
        Image(
            painter = painterResource(card.artRes),
            // 卡名与稀有度都画在图里，读屏用户靠下方那行文字，这里不重复念。
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
            colorFilter = if (owned > 0) null else Desaturate,
            // 光从卡后面长出来，不跟着卡的淡入一起变透明；
            // 但「还没到手」那层压暗还是要的，否则灰卡会亮得像已拥有。
            alpha = cardArtAlpha(owned),
        )
    }
}

/**
 * 一张卡最多能占多大。
 *
 * 两个方向都要收：宽度取可用宽度的 [CARD_WIDTH_FRACTION]，高度再减掉底下文字那一块。
 * 只按宽度算的话，屏幕一矮（小屏、分屏、横屏）卡面就会顶出屏幕。
 */
internal fun calculateCardArtSize(
    availableWidth: Dp,
    availableHeight: Dp,
    aspectRatio: Float,
): DpSize {
    val byWidth = availableWidth * CARD_WIDTH_FRACTION
    val byHeight = (availableHeight - StageFooterReserve) * aspectRatio
    val width = minOf(byWidth, byHeight).coerceAtLeast(0.dp)
    return DpSize(width = width, height = width / aspectRatio)
}
