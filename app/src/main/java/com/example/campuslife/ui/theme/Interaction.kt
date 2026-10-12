package com.example.campuslife.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * 全应用共用的按压手感。只管「看起来 / 摸起来」，不碰任何点击逻辑：
 * 所有效果都从 InteractionSource 读按压状态，点击回调照旧由各自的 clickable 触发。
 *
 * - [ClaudePressIndication]：普通 `Modifier.clickable` 的默认反馈（由主题注入 LocalIndication），
 *   按下时内容微缩、从触点漫开一圈暖色光，松手带一点回弹。
 * - [pressBounce]：给小按钮、标签、卡片用的弹性缩放。
 * - [tactileKey]：像素游戏里的「实体按键」——按钮下方垫一条深色键座，按下时键面沉进去。
 * - [observePress]：只旁听按下 / 松开、不消费事件，给自带手势的控件（任务计时键）补按压态。
 */

/** 按压动画：按下要跟手，松手要弹。 */
private val PressInSpec = spring<Float>(stiffness = 1600f)
private val ReleaseSpec = spring<Float>(dampingRatio = 0.42f, stiffness = 420f)

/** 普通 clickable 按到底时，长边最多缩进这么多（大面积元素几乎不缩，小元素更明显）。 */
private val PressShrink = 6.dp

/**
 * 普通 clickable 的默认按压反馈。由 [CampusLifeTheme] 通过 LocalIndication 注入，
 * 任务行、资料行、序号块这类直接写 `Modifier.clickable { }` 的地方都会自动用上。
 */
internal data class ClaudePressIndication(val glow: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressGlowNode(interactionSource, glow)
}

private class PressGlowNode(
    private val interactionSource: InteractionSource,
    private val glow: Color,
) : Modifier.Node(), DrawModifierNode {

    private val press = Animatable(0f)
    private var origin = Offset.Unspecified

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        origin = interaction.pressPosition
                        press.animateTo(1f, PressInSpec) { invalidateDraw() }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        // 轻点时按下动画往往还没走完就松手了：先补到底再回弹，保证每次点击都看得见。
                        if (press.value < 0.6f) press.animateTo(1f, tween(70)) { invalidateDraw() }
                        press.animateTo(0f, ReleaseSpec) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = press.value
        if (p == 0f || size.minDimension <= 0f) {
            drawContent()
            return
        }
        val longest = max(size.width, size.height)
        val shrink = (PressShrink.toPx() / longest).coerceAtMost(0.05f)
        // p 回弹时会短暂小于 0，内容随之微微放大一下，就是「弹回来」的那一下。
        scale(1f - shrink * p, pivot = center) { this@draw.drawContent() }
        if (p > 0f) {
            // 整屏遮罩这类大面积可点区域只给很淡的光，不抢内容。
            val strength = if (longest > 480.dp.toPx()) 0.05f else 0.13f
            val alpha = strength * p
            val o = if (origin.isSpecified) origin else center
            val radius = longest * (0.35f + 0.75f * p)
            clipRect {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = alpha), glow.copy(alpha = alpha * 0.3f)),
                        center = o,
                        radius = radius,
                    ),
                    radius = radius,
                    center = o,
                )
            }
        }
    }
}

/** Material 组件（Surface / Chip / 导航栏）的水波：颜色仍跟内容色走，只把按下时的浓度调高一点。 */
@OptIn(ExperimentalMaterial3Api::class)
internal val ClaudeRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.12f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.2f,
    ),
)

// ---------------------------------------------------------------- 弹性缩放

/**
 * 按下缩到 [pressedScale]，松手带回弹地放回来。只改绘制层，不影响布局与点击区域。
 * [interactionSource] 必须是同一个控件 clickable 用的那一个。
 */
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.92f,
): Modifier = this then PressBounceElement(interactionSource, pressedScale)

private data class PressBounceElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressBounceNode>() {
    override fun create() = PressBounceNode(interactionSource, pressedScale)
    override fun update(node: PressBounceNode) = node.update(interactionSource, pressedScale)
}

private class PressBounceNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {

    private val scale = Animatable(1f)
    private var job: Job? = null

    override fun onAttach() = collect()

    fun update(source: InteractionSource, newPressedScale: Float) {
        pressedScale = newPressedScale
        if (source != interactionSource) {
            interactionSource = source
            job?.cancel()
            if (isAttached) collect()
        }
    }

    private fun collect() {
        job = coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> scale.animateTo(pressedScale, PressInSpec)
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val halfway = 1f - (1f - pressedScale) * 0.6f
                        if (scale.value > halfway) scale.animateTo(pressedScale, tween(60))
                        scale.animateTo(1f, ReleaseSpec)
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val s = scale.value
                scaleX = s
                scaleY = s
            }
        }
    }
}

// ---------------------------------------------------------------- 实体按键

/**
 * 实体按键：在控件下方垫一条 [depth] 高的键座（[ledgeColor]），按下时键面沉到键座上。
 *
 * 会让控件总高度多出 [depth]；键面本身的大小不变。[shape] 要和键面的形状一致，
 * 键座才能严丝合缝地露出一条边。[ledgeColor] 传透明即不画键座（例如禁用态）。
 */
fun Modifier.tactileKey(
    interactionSource: InteractionSource,
    shape: Shape,
    ledgeColor: Color,
    depth: Dp = 4.dp,
): Modifier = this then TactileKeyElement(interactionSource, shape, ledgeColor, depth)

private data class TactileKeyElement(
    val interactionSource: InteractionSource,
    val shape: Shape,
    val ledgeColor: Color,
    val depth: Dp,
) : ModifierNodeElement<TactileKeyNode>() {
    override fun create() = TactileKeyNode(interactionSource, shape, ledgeColor, depth)
    override fun update(node: TactileKeyNode) = node.update(interactionSource, shape, ledgeColor, depth)
}

private class TactileKeyNode(
    private var interactionSource: InteractionSource,
    private var shape: Shape,
    private var ledgeColor: Color,
    private var depth: Dp,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

    /** 0 = 弹起，1 = 完全按下（键面贴到键座上）。 */
    private val sink = Animatable(0f)
    private var job: Job? = null

    override fun onAttach() = collect()

    fun update(source: InteractionSource, newShape: Shape, newLedge: Color, newDepth: Dp) {
        shape = newShape
        ledgeColor = newLedge
        if (newDepth != depth) {
            depth = newDepth
            invalidateMeasurement()
        }
        if (source != interactionSource) {
            interactionSource = source
            job?.cancel()
            if (isAttached) collect()
        }
        invalidateDraw()
    }

    private fun collect() {
        job = coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> sink.animateTo(1f, PressInSpec)
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        if (sink.value < 0.6f) sink.animateTo(1f, tween(60))
                        sink.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val depthPx = depth.roundToPx()
        val placeable = measurable.measure(constraints.offset(vertical = -depthPx))
        return layout(placeable.width, placeable.height + depthPx) {
            // 回弹时 sink 会略小于 0，键面就轻轻往上跳一下。
            placeable.place(0, (sink.value.coerceAtLeast(-0.35f) * depthPx).roundToInt())
        }
    }

    override fun ContentDrawScope.draw() {
        val depthPx = depth.toPx()
        val face = Size(size.width, size.height - depthPx)
        if (ledgeColor.alpha > 0f && face.height > 0f) {
            val outline = shape.createOutline(face, layoutDirection, this)
            translate(top = depthPx) { drawOutline(outline, ledgeColor) }
        }
        drawContent()
    }
}

// ---------------------------------------------------------------- 内嵌键唇

/**
 * 小气泡用的「内嵌键唇」：在控件底边内侧画一条 [depth] 高的深色唇边，按下时唇边收薄，
 * 像键帽被按了下去。与 [tactileKey] 不同，它**不改变控件尺寸**，适合已经排好版的小气泡；
 * 要挂在会被裁成圆角的那一层里（例如 Surface 的内容 Row 上、padding 之前），唇边才会跟着圆角走。
 */
fun Modifier.keyLip(
    interactionSource: InteractionSource,
    color: Color,
    depth: Dp = 3.dp,
): Modifier = this then KeyLipElement(interactionSource, color, depth)

private data class KeyLipElement(
    val interactionSource: InteractionSource,
    val color: Color,
    val depth: Dp,
) : ModifierNodeElement<KeyLipNode>() {
    override fun create() = KeyLipNode(interactionSource, color, depth)
    override fun update(node: KeyLipNode) = node.update(interactionSource, color, depth)
}

private class KeyLipNode(
    private var interactionSource: InteractionSource,
    private var color: Color,
    private var depth: Dp,
) : Modifier.Node(), DrawModifierNode {

    private val sink = Animatable(0f)
    private var job: Job? = null

    override fun onAttach() = collect()

    fun update(source: InteractionSource, newColor: Color, newDepth: Dp) {
        color = newColor
        depth = newDepth
        if (source != interactionSource) {
            interactionSource = source
            job?.cancel()
            if (isAttached) collect()
        }
        invalidateDraw()
    }

    private fun collect() {
        job = coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> sink.animateTo(1f, PressInSpec) { invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        if (sink.value < 0.6f) sink.animateTo(1f, tween(60)) { invalidateDraw() }
                        sink.animateTo(0f, ReleaseSpec) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val lip = depth.toPx() * (1f - 0.7f * sink.value.coerceIn(0f, 1f))
        if (color.alpha > 0f && lip > 0f) {
            drawRect(color, topLeft = Offset(0f, size.height - lip), size = Size(size.width, lip))
        }
        // 按下时内容跟着往下沉一点点。
        translate(top = depth.toPx() * 0.35f * sink.value) { this@draw.drawContent() }
    }
}

// ---------------------------------------------------------------- 旁听按压

/**
 * 只旁听手指按下 / 抬起，不消费任何事件，所以不会改变控件原有的手势判定。
 * 给自己处理手势的控件（例如任务计时键的「短按 / 按住 3 秒」）补一个按压态。
 *
 * 以 [pressed] 这个状态对象本身作 key：按住期间控件每帧重组也不会把旁听重启掉。
 */
fun Modifier.observePress(pressed: MutableState<Boolean>): Modifier =
    pointerInput(pressed) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed.value = true
            try {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            } finally {
                pressed.value = false
            }
        }
    }
