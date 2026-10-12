package com.example.campuslife.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.pressBounce
import com.example.campuslife.ui.theme.tactileKey

/*
 * 小卵包的按钮家族。参数与 Material 3 同名按钮保持一致（onClick / enabled / colors / shape …），
 * 调用处只换名字，点击、禁用、配色的语义都不变；变的只有样子和手感：
 *
 * - ClaudeButton / ClaudeTonalButton / ClaudeOutlinedButton：像素游戏里的实体按键，
 *   下方垫一条深色键座，按下时键面沉进去，松手弹回，伴一次轻触感。
 * - ClaudeTextButton：按下时浮起一枚药丸底色并弹性缩放。
 * - ClaudeIconButton：按下时从中心长出圆形底色，图标弹一下。
 * - ClaudeFilterChip：按压缩放，选中态切换时「啵」地跳一下。
 */

/** 实体按键统一用 12dp 圆角，和卡片、打卡气泡同一套圆角语言。 */
private val KeyShape = RoundedCornerShape(12.dp)
private val KeyDepth = 4.dp

@Composable
fun ClaudeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = KeyShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    KeyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = container,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        ledgeColor = if (enabled) lerp(container, Color.Black, 0.26f) else Color.Transparent,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ClaudeTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = KeyShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    KeyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = container,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        ledgeColor = if (enabled) lerp(container, Color.Black, 0.14f) else Color.Transparent,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ClaudeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = KeyShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    // 描边按钮原本是透明底；实体按键的键座会从透明的键面里透出来，所以垫一层面板色。
    val container = (if (enabled) colors.containerColor else colors.disabledContainerColor)
        .compositeOver(surface)
    KeyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = container,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        ledgeColor = if (enabled) outline else Color.Transparent,
        border = border ?: BorderStroke(
            1.dp,
            if (enabled) outline else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun KeyButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    ledgeColor: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    // 启用 / 禁用、状态换色时不再一下子跳变，而是顺滑地过渡过去。
    val face by animateColorAsState(containerColor, label = "keyFace")
    val tint by animateColorAsState(contentColor, label = "keyTint")
    val ledge by animateColorAsState(ledgeColor, label = "keyLedge")

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .tactileKey(interactionSource, shape, ledge, KeyDepth)
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
            .clip(shape)
            .background(face)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides tint,
            LocalTextStyle provides LocalTextStyle.current.merge(MaterialTheme.typography.labelLarge),
        ) {
            content()
        }
    }
}

@Composable
fun ClaudeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(percent = 50),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val tint by animateColorAsState(
        if (enabled) colors.contentColor else colors.disabledContentColor,
        label = "textButtonTint",
    )
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    // 按下时浮起一枚药丸底色；松手慢慢褪掉，轻点也看得见。
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(90) else tween(280),
        label = "textButtonGlow",
    )

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressBounce(interactionSource, pressedScale = 0.9f)
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
            .clip(shape)
            .background(container)
            .drawBehind {
                if (glow > 0f) drawRect(tint.copy(alpha = 0.14f * glow))
            }
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides tint,
            LocalTextStyle provides LocalTextStyle.current.merge(MaterialTheme.typography.labelLarge),
        ) {
            content()
        }
    }
}

@Composable
fun ClaudeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val tint = if (enabled) colors.contentColor else colors.disabledContentColor
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(110) else tween(300),
        label = "iconButtonGlow",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .pressBounce(interactionSource, pressedScale = 0.8f)
            .clip(CircleShape)
            .background(container)
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = 0.15f * glow),
                        radius = size.minDimension / 2f * (0.5f + 0.5f * glow),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint, content = content)
    }
}

@Composable
fun ClaudeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    // 选中态变化时「啵」一下：先收一点再带回弹放开。首次出现不跳。
    val pop = remember { Animatable(1f) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!appeared) {
            appeared = true
            return@LaunchedEffect
        }
        pop.snapTo(0.86f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
    }

    FilterChip(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        label = label,
        modifier = modifier
            .pressBounce(interactionSource, pressedScale = 0.92f)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = interactionSource,
    )
}
