package com.example.campuslife.ui.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GachaAvailability
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.theme.claude
import kotlin.math.sin

/** 前卡宽度：比右侧的打卡气泡略窄一点，不抢首屏注意力。 */
private val FrontCardWidth = 100.dp

/** 封面的宽高比（800×955）。整叠卡都按素材比例摆，不要写死成别的数。 */
private const val CoverAspectRatio = 0.8377f

/** 卡面圆角。与首页其它气泡同一档。 */
private val CardCornerRadius = 12.dp

/**
 * 卡框：第一弹的卡面就是「奶油底 + 一圈陶土色的框 + 里面一道细线」（见设计稿）。
 *
 * 整叠卡都沿用这一圈，空弹才不会像一块灰板子。框色走 `claude.accent`（就是卡面那个陶土色），
 * 底色走 `claude.accentSoft`（同一族的奶油色，明暗主题各有取值，不写字面量）。
 */
private val CardFrameWidth = 1.5.dp
private const val CardFrameAlpha = 0.7f

/** 里面那道细线离卡边的距离：封面铺满时看不见，空弹时它就是「这是一张卡」的全部信息。 */
private val InnerFrameInset = 3.dp

/**
 * 后面那几张每往外挪多远、每张多转多少度。
 *
 * 这两个数是这叠卡的「厚度」旋钮：步长决定露出来的边有多宽，角度决定它斜多少。
 * 只调手感就改这里，别去动布局结构。
 */
private val BackCardStep = 8.dp
private const val BackCardAngleStep = 3f

/**
 * 后面最多铺几张。
 *
 * 手势向左滑是「把前卡掀到后面去」，所以后面那几张要够看；四张与设计稿上的叠法一致。
 * 超过这个数不再往外铺——再铺就出画面了。
 */
private const val MaxBackLayers = 4

/** 横向拖过这么多才算一次翻页，太小会把手指的抖动也当成翻页。 */
private val SwipeThreshold = 24.dp

/**
 * 首页的抽卡入口：一叠卡，最上面那张就是当前这一弹。
 *
 * **卡面风格照第一弹的设计稿**：奶油底 + 一圈陶土色的框 + 里面一道细线。
 * 所以空弹不是一块灰板子，而是同一套风格里「还没内容」的一张卡；颜色全走
 * `claude.accent` / `claude.accentSoft`，明暗主题各有取值，不写死色值。
 *
 * 前卡贴右边缘、**不旋转**（封面里的插画与标题要正着看），后面几弹依次向左探出一条边、
 * 逐张多转一点，绕各自的**右下角**转，所以翘起来的是左边、底边始终齐平——
 * 不会比前卡低一截，跟右上角打卡行的下沿仍是同一条线。
 *
 * 左右滑动切换当前弹：往左滑把前卡掀到后面（看下一弹），往右滑回来，滑到头就停住。
 * 已上线弹展示各自封面、收集进度和每日抽取状态；未上线弹仍是空白卡面，点不动、也没有状态字。
 * 选中的弹只存在于组件内部，点击时把对应 [CardSeries] 交给外层执行抽取。
 *
 * **宽度由调用方实测后传进来**（[stackWidth]）：它渲染在首页头部那一列里，
 * 而那一列的宽度上限正是上方「打卡那一行」的宽度，按它铺就不会把外层 Row 撑宽或挤走时钟。
 */
@Composable
internal fun GachaSeriesStack(
    series: List<GachaSeriesSlot>,
    onClick: (CardSeries) -> Unit,
    modifier: Modifier = Modifier,
    stackWidth: Dp = FrontCardWidth + BackCardStep * MaxBackLayers,
    frontCardWidth: Dp = FrontCardWidth,
) {
    if (series.isEmpty()) return

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selected = selectedIndex.coerceIn(0, series.lastIndex)
    val frontSlot = series[selected]
    val frontSeries = frontSlot.cardSeries
    val hasContent = frontSlot.coverRes != null && frontSeries != null
    val canDraw = hasContent && frontSlot.availability.canDraw
    val statusText = when {
        !hasContent -> null
        frontSlot.availability.hint != null -> frontSlot.availability.hint
        else -> "抽一次 · ${frontSlot.ownedKinds}/${frontSlot.setSize}"
    }

    val cardHeight = frontCardWidth / CoverAspectRatio
    val fanWidth = (stackWidth - frontCardWidth).coerceAtLeast(0.dp)
    val backLayers = visibleBackLayers(series.size, fanWidth, BackCardStep)
    // 绕右下角转 θ 会把左边那一头抬起「卡宽 × sinθ」：给这一段留出高度，别被父布局裁掉。
    val headroom = frontCardWidth * sin(Math.toRadians((BackCardAngleStep * backLayers).toDouble()))
        .toFloat() + 6.dp

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
    var dragPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(stackWidth.coerceAtLeast(frontCardWidth))
            .height(cardHeight + headroom)
            .pointerInput(series.size, selected) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPx = 0f },
                    onDragEnd = {
                        val step = when {
                            dragPx <= -swipeThresholdPx -> 1
                            dragPx >= swipeThresholdPx -> -1
                            else -> 0
                        }
                        selectedIndex = moveSeriesIndex(selected, step, series.size)
                        dragPx = 0f
                    },
                ) { _, delta -> dragPx += delta }
            },
    ) {
        // 后面的弹：只露左边一条边。越靠后挪得越远、转得越多。
        val backSlots = series.filterIndexed { index, _ -> index != selected }
        for (layer in backLayers downTo 1) {
            val slot = backSlots.getOrNull(layer - 1) ?: continue
            SeriesCard(
                slot = slot,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -(BackCardStep * layer))
                    .graphicsLayer {
                        rotationZ = BackCardAngleStep * layer
                        transformOrigin = TransformOrigin(1f, 1f)
                    },
                width = frontCardWidth,
            )
        }

        SeriesCard(
            slot = frontSlot,
            modifier = Modifier.align(Alignment.BottomEnd),
            width = frontCardWidth,
            statusText = statusText,
            canDraw = canDraw,
            onClick = frontSeries?.let { selectedSeries -> { onClick(selectedSeries) } },
            showNumber = true,
        )
    }
}

/**
 * 一张卡面。
 *
 * [onClick] 为 null 时是不可点的（后面那几张只是「还有别的弹」的示意，点它们不做事）；
 * [statusText] 为 null 时不画底部那条渐变带——空弹没有状态可说，就不占那一行高度。
 */
@Composable
private fun SeriesCard(
    slot: GachaSeriesSlot,
    width: Dp,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    canDraw: Boolean = false,
    onClick: (() -> Unit)? = null,
    showNumber: Boolean = false,
) {
    val shape = RoundedCornerShape(CardCornerRadius)
    val hasCover = slot.coverRes != null
    val cardModifier = modifier
        .width(width)
        .aspectRatio(CoverAspectRatio)
    val border = BorderStroke(
        width = CardFrameWidth,
        color = MaterialTheme.claude.accent.copy(alpha = CardFrameAlpha),
    )

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            slot.coverRes?.let { cover ->
                Image(
                    painter = painterResource(cover),
                    contentDescription = "第 ${slot.number} 弹封面",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (canDraw) 1f else 0.5f),
                    contentScale = ContentScale.Crop,
                )
                if (statusText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = if (canDraw) {
                                MaterialTheme.claude.accentStrong
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            // 空弹没有封面，里面那道细线就是「这是一张卡」的全部信息。
            if (!hasCover) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(InnerFrameInset)
                        .border(1.dp, MaterialTheme.claude.accent.copy(alpha = 0.35f), shape),
                )
            }

            if (showNumber) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.claude.accentSoft,
                    contentColor = MaterialTheme.claude.accent,
                    border = BorderStroke(1.dp, MaterialTheme.claude.accent.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = slot.number.toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = canDraw,
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.claude.accentSoft,
            border = border,
            // 能抽时给一点抬升，让它比旁边的打卡气泡更「想被点」。
            shadowElevation = if (canDraw) 2.dp else 0.dp,
        ) { content() }
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.claude.accentSoft,
            border = border,
        ) { content() }
    }
}

/**
 * 后面能露出几层：受总弹数与可用宽度双重限制，露不出来就不铺。
 */
private fun visibleBackLayers(seriesCount: Int, fanWidth: Dp, step: Dp): Int {
    if (seriesCount <= 1 || fanWidth <= 0.dp || step <= 0.dp) return 0
    return minOf(seriesCount - 1, MaxBackLayers, (fanWidth / step).toInt())
}

/**
 * 左右滑动之后停在第几弹。
 *
 * 夹在首尾之间：滑到头就停住，不会绕回另一端（返回 `step = 0` 时原样返回）。
 */
internal fun moveSeriesIndex(current: Int, step: Int, count: Int): Int {
    if (count <= 0) return 0
    return (current + step).coerceIn(0, count - 1)
}

@Preview(name = "可抽", group = "Gacha", showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun GachaSeriesStackReadyPreview() {
    CampusLifeTheme(darkTheme = false) {
        GachaSeriesStack(
            series = DefaultGachaSeries.map { slot ->
                if (slot.cardSeries == CardSeries.Isekai) {
                    slot.copy(availability = GachaAvailability.Ready, ownedKinds = 3)
                } else {
                    slot
                }
            },
            onClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Preview(name = "不能抽", group = "Gacha", showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun GachaSeriesStackBlockedPreview() {
    CampusLifeTheme(darkTheme = false) {
        GachaSeriesStack(
            series = DefaultGachaSeries,
            onClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Preview(name = "空弹 · 暗色", group = "Gacha", showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun GachaSeriesStackEmptyPreview() {
    CampusLifeTheme(darkTheme = true) {
        GachaSeriesStack(
            series = listOf(GachaSeriesSlot(number = 1)),
            onClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
