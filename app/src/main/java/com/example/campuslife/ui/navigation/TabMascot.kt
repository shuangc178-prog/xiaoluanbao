package com.example.campuslife.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val MascotBody = Color(0xFFD67857)
private val MascotEye = Color(0xFF1F1E1C)

/** 像素小卵包的一格；整只 12 × 8 格。 */
private val MascotUnit = 2.5.dp

/** 起跳最多能跳多高（画布在精灵上方预留的空间）。 */
private val HopRoom = 22.dp

/** 与 Material NavigationBar 的条目间距一致，算出来的站位才能正好落在每个 Tab 中心。 */
private val NavItemSpacing = 8.dp

private enum class MascotEyes { Open, Blink, Happy }

/**
 * 底部导航栏上沿站着的像素小卵包。
 *
 * - 它总站在当前 Tab 的正上方；切 Tab 时朝那边一蹦跳过去，落地压扁一下再弹回来。
 * - 再点一次当前 Tab（[pokeCount] 加一），它就原地开心地跳一下。
 * - 平时每隔几秒眨一次眼。
 *
 * 只画画、不处理触摸：它盖在内容上沿那一条，手指照样点得到下面的东西。
 */
@Composable
internal fun TabMascot(
    selectedIndex: Int,
    tabCount: Int,
    pokeCount: Int,
    modifier: Modifier = Modifier,
) {
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val hop = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    var hopScale by remember { mutableFloatStateOf(1f) }
    var look by remember { mutableFloatStateOf(0f) }
    var eyes by remember { mutableStateOf(MascotEyes.Open) }

    suspend fun land() {
        hop.snapTo(0f)
        squash.snapTo(1f)
        squash.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 520f))
    }

    LaunchedEffect(selectedIndex) {
        val from = position.value
        val to = selectedIndex.toFloat()
        if (from == to) return@LaunchedEffect
        val distance = abs(to - from)
        look = if (to > from) 0.3f else -0.3f
        eyes = MascotEyes.Open
        hopScale = (0.6f + 0.2f * distance).coerceAtMost(1f)
        val duration = (240 + 110 * distance).roundToInt().coerceAtMost(600)
        hop.snapTo(0f)
        coroutineScope {
            launch { position.animateTo(to, tween(duration, easing = FastOutSlowInEasing)) }
            launch { hop.animateTo(1f, tween(duration, easing = LinearEasing)) }
        }
        look = 0f
        eyes = MascotEyes.Happy
        land()
        delay(450)
        eyes = MascotEyes.Open
    }

    LaunchedEffect(pokeCount) {
        if (pokeCount == 0) return@LaunchedEffect
        eyes = MascotEyes.Happy
        hopScale = 0.75f
        hop.snapTo(0f)
        hop.animateTo(1f, tween(380, easing = LinearEasing))
        land()
        delay(500)
        eyes = MascotEyes.Open
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2600L + Random.nextLong(2400L))
            if (eyes == MascotEyes.Open) {
                eyes = MascotEyes.Blink
                delay(130)
                if (eyes == MascotEyes.Blink) eyes = MascotEyes.Open
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(MascotUnit * 8 + HopRoom)
                .graphicsLayer(),
        ) {
            val u = MascotUnit.toPx()
            val spacing = NavItemSpacing.toPx()
            val count = tabCount.coerceAtLeast(1)
            val itemWidth = (size.width - spacing * (count - 1)) / count
            val cx = position.value * (itemWidth + spacing) + itemWidth / 2f
            val airborne = hop.value > 0f && hop.value < 1f
            val lift = sin(PI * hop.value).toFloat() * HopRoom.toPx() * hopScale
            val feetY = size.height - lift

            // 影子：跳得越高越小越淡。
            val shadow = 1f - (lift / HopRoom.toPx()).coerceIn(0f, 1f) * 0.55f
            drawOval(
                color = Color.Black.copy(alpha = 0.12f * shadow),
                topLeft = Offset(cx - 5f * u * shadow, size.height - 0.7f * u),
                size = Size(10f * u * shadow, 1.4f * u),
            )

            val sq = squash.value
            withTransform({ scale(1f + 0.2f * sq, 1f - 0.2f * sq, pivot = Offset(cx, feetY)) }) {
                drawMascot(
                    left = cx - 6f * u,
                    top = feetY - 8f * u,
                    u = u,
                    eyes = eyes,
                    look = look,
                    tucked = airborne,
                    armsUp = eyes == MascotEyes.Happy,
                )
            }
        }
    }
}

private fun DrawScope.drawMascot(
    left: Float,
    top: Float,
    u: Float,
    eyes: MascotEyes,
    look: Float,
    tucked: Boolean,
    armsUp: Boolean,
) {
    fun cell(x: Float, y: Float, w: Float, h: Float, color: Color = MascotBody) {
        drawRect(color, topLeft = Offset(left + x * u, top + y * u), size = Size(w * u, h * u))
    }
    // 身体 8 × 6，两侧手臂，四条腿（与 App 图标、启动视频里的小卵包同一套比例）。
    cell(2f, 0f, 8f, 6f)
    if (armsUp) {
        cell(0f, 0.5f, 2f, 2f)
        cell(-0.8f, -1.3f, 2f, 2f)
        cell(10f, 0.5f, 2f, 2f)
        cell(10.8f, -1.3f, 2f, 2f)
    } else {
        cell(0f, 2f, 2f, 2f)
        cell(10f, 2f, 2f, 2f)
    }
    val legHeight = if (tucked) 1.2f else 2f
    for (x in floatArrayOf(2f, 4f, 7f, 9f)) cell(x, 6f, 1f, legHeight)

    for (ex in floatArrayOf(3f + look, 8f + look)) {
        when (eyes) {
            MascotEyes.Open -> cell(ex, 1f, 1f, 1f, MascotEye)
            MascotEyes.Blink -> cell(ex - 0.1f, 1.6f, 1.2f, 0.3f, MascotEye)
            MascotEyes.Happy -> {
                cell(ex - 0.25f, 1.5f, 0.5f, 0.5f, MascotEye)
                cell(ex + 0.25f, 1f, 0.5f, 0.5f, MascotEye)
                cell(ex + 0.75f, 1.5f, 0.5f, 0.5f, MascotEye)
            }
        }
    }
}
