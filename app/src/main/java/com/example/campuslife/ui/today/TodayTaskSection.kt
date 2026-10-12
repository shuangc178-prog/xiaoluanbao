package com.example.campuslife.ui.today

import androidx.compose.runtime.State
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.example.campuslife.ui.theme.observePress
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.campuslife.ui.components.ClaudeTextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.data.plan.formatLearningDuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.reminder.TaskTimeChip
import com.example.campuslife.ui.reminder.TimePickerDialog
import com.example.campuslife.ui.reminder.TimePickerField
import com.example.campuslife.ui.reminder.defaultMinuteOfDay
import com.example.campuslife.ui.theme.claude

/**
 * 「任务栏」分区：展示当天的全部任务，内容超过一屏时由外层 LazyColumn 滚动。
 *
 * 长按结束以 [completedTitles] 为准——它是当天计划存档里的完成状态，
 * 而不是 [TodayPriorityUiModel.status]，这样完成结果能跨 Tab、跨重启保留。
 *
 * [isLocked] 为 true 表示当天已结束打卡：**任务整体只读**。
 * 界面只做两件事——计时按钮禁用、「添加」收起——真正拦住写入的是 [DailyPlanController]。
 * 按第 16.25 节的口径不写解释句子，锁定状态靠状态字「已打卡」与禁用态表达。
 */
@Composable
internal fun TodayPrioritiesSection(
    priorities: List<TodayPriorityUiModel>,
    completedTitles: Set<String>,
    onPriorityClick: (String) -> Unit,
    onPriorityToggle: (String) -> Unit,
    onPriorityFinish: (String) -> Unit,
    onUseAutomaticOrder: () -> Unit,
    onAddPriorityClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    isManualOrder: Boolean = false,
) {
    val completedCount = priorities.count { it.title in completedTitles }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "任务栏",
            trailingText = when {
                priorities.isEmpty() -> null
                isLocked -> "$completedCount / ${priorities.size} · 已打卡"
                else -> "$completedCount / ${priorities.size}"
            },
            // 锁定后不再收新任务：打卡日的任务集合一旦定下，再插一条未完成的任务会让
            // 这一天的存档看起来并没有全部完成。
            actionLabel = if (isLocked) null else "添加",
            onActionClick = onAddPriorityClick,
        )

        if (priorities.isEmpty()) {
            EmptySectionCard(
                title = "任务栏还是空的",
                supportingText = "挑一件真正重要的事记录下来。",
                actionLabel = "添加任务",
                onActionClick = onAddPriorityClick,
                modifier = Modifier.padding(top = 12.dp),
            )
            return
        }

        TaskOrderModeRow(
            isManualOrder = isManualOrder,
            enabled = !isLocked,
            onUseAutomaticOrder = onUseAutomaticOrder,
            modifier = Modifier.padding(top = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            priorities.forEachIndexed { index, priority ->
                TodayPriorityRow(
                    priority = priority,
                    isCompleted = priority.title in completedTitles,
                    isLocked = isLocked,
                    onClick = { onPriorityClick(priority.id) },
                    onToggle = { onPriorityToggle(priority.id) },
                    onFinish = { onPriorityFinish(priority.id) },
                )
                if (index < priorities.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/**
 * 今日任务与打卡都收尾后出现的「明日任务安排」。
 *
 * 这里不放计时按钮：明日任务只是预排，到了次日凌晨 6 点成为任务栏后才能执行。
 */
@Composable
internal fun TomorrowPlanSection(
    priorities: List<TodayPriorityUiModel>,
    dateLabel: String,
    onPriorityClick: (String) -> Unit,
    onUseAutomaticOrder: () -> Unit,
    onAddPriorityClick: () -> Unit,
    modifier: Modifier = Modifier,
    isManualOrder: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "明日任务安排",
            trailingText = dateLabel,
            actionLabel = "添加",
            onActionClick = onAddPriorityClick,
        )

        if (priorities.isEmpty()) {
            EmptySectionCard(
                title = "明日还没有安排",
                supportingText = "趁今天已经收尾，挑几件明天最重要的事。",
                actionLabel = "安排任务",
                onActionClick = onAddPriorityClick,
                modifier = Modifier.padding(top = 12.dp),
            )
            return
        }


        TaskOrderModeRow(
            isManualOrder = isManualOrder,
            enabled = true,
            onUseAutomaticOrder = onUseAutomaticOrder,
            modifier = Modifier.padding(top = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            priorities.forEachIndexed { index, priority ->
                TomorrowPriorityRow(
                    priority = priority,
                    onClick = { onPriorityClick(priority.id) },
                )
                if (index < priorities.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 58.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/** 新增与编辑共用的任务表单，保存动作由外层控制器持久化。 */
@Composable
internal fun TaskEditorDialog(
    priority: TodayPriorityUiModel?,
    existingTitles: Set<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, supportingText: String, expectedStartMinute: Int?) -> Unit,
    onDelete: (() -> Unit)?,
    taskScopeLabel: String? = null,
    initialPosition: Int? = null,
    positionCount: Int = 0,
    positionEnabled: Boolean = true,
    onPositionSelect: (Int) -> Unit = {},
) {
    var title by remember(priority?.id) { mutableStateOf(priority?.title.orEmpty()) }
    var supportingText by remember(priority?.id) {
        mutableStateOf(priority?.supportingText.orEmpty())
    }
    var expectedStartMinute by remember(priority?.id) {
        mutableStateOf(priority?.expectedStartMinute)
    }
    var showTimePicker by remember(priority?.id) { mutableStateOf(false) }
    var selectedPosition by remember(priority?.id) {
        mutableStateOf(initialPosition ?: 0)
    }

    val normalizedTitle = title.trim()
    val isDuplicate = normalizedTitle.isNotEmpty() && existingTitles.any { existing ->
        existing != priority?.title && existing == normalizedTitle
    }
    val canSave = normalizedTitle.isNotEmpty() && !isDuplicate

    if (showTimePicker) {
        TimePickerDialog(
            initialMinuteOfDay = expectedStartMinute ?: defaultMinuteOfDay(),
            onConfirm = {
                expectedStartMinute = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val scope = taskScopeLabel.orEmpty()
            Text(if (priority == null) "添加${scope}任务" else "编辑${scope}任务")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任务名称") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("${taskScopeLabel ?: "今天"}已有同名任务") }
                    } else {
                        null
                    },
                )
                OutlinedTextField(
                    value = supportingText,
                    onValueChange = { supportingText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("补充说明（可选）") },
                    placeholder = { Text("例如：高等数学 · 40 分钟") },
                    minLines = 2,
                    maxLines = 3,
                )
                TimePickerField(
                    minuteOfDay = expectedStartMinute,
                    label = "预期开始时间",
                    onPick = { showTimePicker = true },
                    onClear = { expectedStartMinute = null },
                )
                if (priority != null && initialPosition != null && positionCount > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "任务顺序",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TaskPositionSelector(
                            selectedIndex = selectedPosition,
                            positionCount = positionCount,
                            enabled = positionEnabled,
                            onSelect = { targetIndex ->
                                selectedPosition = targetIndex
                                onPositionSelect(targetIndex)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            ClaudeTextButton(
                onClick = {
                    onSave(normalizedTitle, supportingText.trim(), expectedStartMinute)
                },
                enabled = canSave,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    ClaudeTextButton(onClick = onDelete) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                ClaudeTextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun TodayPriorityRow(
    priority: TodayPriorityUiModel,
    isCompleted: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = if (isCompleted) TaskLearningPhase.Finished else priority.learningPhase
    val titleColor = when (phase) {
        TaskLearningPhase.Running -> MaterialTheme.claude.green
        TaskLearningPhase.Paused -> MaterialTheme.claude.danger
        TaskLearningPhase.Finished -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskLearningPhase.Idle -> MaterialTheme.colorScheme.onSurface
    }
    val highlight = when (phase) {
        TaskLearningPhase.Running -> MaterialTheme.claude.greenSoft
        TaskLearningPhase.Paused -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskLearningButton(
            phase = phase,
            enabled = !isLocked && !isCompleted,
            title = priority.title,
            onClick = onToggle,
            onFinish = onFinish,
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(6.dp)).background(highlight).padding(6.dp),
        ) {
            Text(
                text = priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (priority.supportingText.isNotBlank()) {
                Text(
                    text = priority.supportingText,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = titleColor,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                )
            }
            if (phase != TaskLearningPhase.Idle) {
                val label = when (phase) {
                    TaskLearningPhase.Running -> "计时中"
                    TaskLearningPhase.Paused -> "已暂停"
                    TaskLearningPhase.Finished -> "已完成"
                    TaskLearningPhase.Idle -> ""
                }
                Text(
                    text = "$label · ${formatLearningDuration(priority.learningDurationMillis)}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = titleColor,
                )
            }
        }
        priority.expectedStartLabel?.let {
            TaskTimeChip(label = it, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** 圆形录制键：短按开始/暂停；持续按住 3 秒，圆环填满后才完成。 */
@Composable
private fun TaskLearningButton(
    phase: TaskLearningPhase,
    enabled: Boolean,
    title: String,
    onClick: () -> Unit,
    onFinish: () -> Unit,
) {
    var holdProgress by remember(title) { mutableFloatStateOf(0f) }
    var accessibilityConfirmation by remember(title) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val confirmedFinish by rememberUpdatedState<() -> Unit> {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onFinish()
    }
    val completionColor = MaterialTheme.claude.green
    LaunchedEffect(enabled, title) {
        if (!enabled) {
            accessibilityConfirmation?.cancel()
            accessibilityConfirmation = null
            holdProgress = 0f
        }
    }
    val color = when (phase) {
        TaskLearningPhase.Running, TaskLearningPhase.Finished -> MaterialTheme.claude.green
        else -> MaterialTheme.claude.danger
    }.copy(alpha = if (enabled || phase == TaskLearningPhase.Finished) 1f else 0.4f)
    val label = when (phase) {
        TaskLearningPhase.Idle -> "开始计时"
        TaskLearningPhase.Running -> "暂停计时"
        TaskLearningPhase.Paused -> "继续计时"
        TaskLearningPhase.Finished -> "已完成"
    }
    // 以下只是手感：按住时按键缩下去，计时中外圈一圈圈「呼吸」，完成的那一刻对勾弹出来。
    // 按压态由 observePress 旁听得到，它不消费事件，短按 / 按住 3 秒的判定完全不受影响。
    val pressed = remember { mutableStateOf(false) }
    val isPressed = pressed.value && enabled
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1f,
        animationSpec = if (isPressed) spring(stiffness = 1500f) else spring(dampingRatio = 0.4f, stiffness = 450f),
        label = "taskButtonPress",
    )
    val finishPop = remember { Animatable(1f) }
    var appeared by remember(title) { mutableStateOf(false) }
    LaunchedEffect(phase == TaskLearningPhase.Finished) {
        if (!appeared) {
            appeared = true
            return@LaunchedEffect
        }
        if (phase == TaskLearningPhase.Finished) {
            finishPop.snapTo(0.5f)
            finishPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
    }
    // 只在绘制阶段读进度：计时可能一开就是几个小时，不能让整颗按钮每帧重组。
    val halo: State<Float>? = if (phase == TaskLearningPhase.Running) {
        rememberInfiniteTransition(label = "taskHalo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearOutSlowInEasing)),
            label = "taskHaloProgress",
        )
    } else {
        null
    }
    val haloColor = MaterialTheme.claude.green
    Box(
        modifier = Modifier.size(48.dp)
            .observePress(pressed)
            .clip(CircleShape)
            .drawBehind {
                val h = halo?.value
                if (h != null) {
                    drawCircle(
                        color = haloColor.copy(alpha = 0.32f * (1f - h)),
                        radius = 14.dp.toPx() + 9.dp.toPx() * h,
                    )
                }
            }
            .taskCompletionGesture(
                taskKey = title,
                enabled = enabled,
                scope = scope,
                onPressStart = {
                    accessibilityConfirmation?.cancel()
                    accessibilityConfirmation = null
                    holdProgress = 0f
                },
                onProgress = { holdProgress = it },
                onTap = { currentOnClick() },
                onFinish = { confirmedFinish() },
            ).semantics {
                role = Role.Button
                if (!enabled) disabled()
                contentDescription = "$title，$label，按住3秒完成任务"
                stateDescription = if (holdProgress > 0f) "正在确认完成" else when (phase) {
                    TaskLearningPhase.Running -> "计时中"
                    TaskLearningPhase.Paused -> "已暂停"
                    TaskLearningPhase.Finished -> "已完成"
                    TaskLearningPhase.Idle -> "未开始"
                }
                this.onClick(label = label) {
                    if (enabled) {
                        accessibilityConfirmation?.cancel()
                        accessibilityConfirmation = null
                        holdProgress = 0f
                        currentOnClick()
                        true
                    } else false
                }
                // 辅助功能菜单同样提供 3 秒动画确认，并可在倒计时中取消。
                customActions = if (!enabled) emptyList() else buildList {
                    add(CustomAccessibilityAction("3秒后完成任务") {
                        if (accessibilityConfirmation?.isActive == true) false else {
                            val hold = TaskCompletionHold(SystemClock.uptimeMillis())
                            accessibilityConfirmation = scope.launch {
                                try {
                                    animateTaskCompletionHold(hold, { holdProgress = it }) { confirmedFinish() }
                                } finally {
                                    holdProgress = 0f
                                    accessibilityConfirmation = null
                                }
                            }
                            true
                        }
                    })
                    if (accessibilityConfirmation?.isActive == true) {
                        add(CustomAccessibilityAction("取消完成") {
                            accessibilityConfirmation?.cancel()
                            accessibilityConfirmation = null
                            holdProgress = 0f
                            true
                        })
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(30.dp).graphicsLayer {
                val s = pressScale * finishPop.value
                scaleX = s
                scaleY = s
            },
        ) {
            drawCircle(color, radius = size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            val c = center
            when (phase) {
                TaskLearningPhase.Idle -> drawCircle(color, radius = 8.dp.toPx())
                TaskLearningPhase.Running -> drawRoundRect(
                    color, topLeft = Offset(c.x - 7.dp.toPx(), c.y - 7.dp.toPx()),
                    size = Size(14.dp.toPx(), 14.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()),
                )
                TaskLearningPhase.Paused -> {
                    drawRoundRect(color, Offset(c.x - 6.dp.toPx(), c.y - 7.dp.toPx()),
                        Size(4.dp.toPx(), 14.dp.toPx()), CornerRadius(1.dp.toPx()))
                    drawRoundRect(color, Offset(c.x + 2.dp.toPx(), c.y - 7.dp.toPx()),
                        Size(4.dp.toPx(), 14.dp.toPx()), CornerRadius(1.dp.toPx()))
                }
                TaskLearningPhase.Finished -> {
                    val check = Path().apply {
                        moveTo(c.x - 7.dp.toPx(), c.y)
                        lineTo(c.x - 2.dp.toPx(), c.y + 5.dp.toPx())
                        lineTo(c.x + 7.dp.toPx(), c.y - 5.dp.toPx())
                    }
                    drawPath(check, color, style = Stroke(2.dp.toPx()))
                }
            }
            if (holdProgress > 0f) {
                val inset = 1.5.dp.toPx()
                drawArc(
                    color = completionColor,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun TomorrowPriorityRow(
    priority: TodayPriorityUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
            ) {
            Text(
                text = priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (priority.supportingText.isNotBlank()) {
                Text(
                    text = priority.supportingText,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            priority.expectedStartLabel?.let { startLabel ->
                TaskTimeChip(label = startLabel)
            }
        }
    }
}

@Composable
private fun TaskOrderModeRow(
    isManualOrder: Boolean,
    enabled: Boolean,
    onUseAutomaticOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isManualOrder) Icons.Filled.SwapVert else Icons.Filled.Schedule,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (isManualOrder) "手动排序" else "按时间自动排序",
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isManualOrder) {
            ClaudeTextButton(onClick = onUseAutomaticOrder, enabled = enabled) {
                Text("恢复按时间")
            }
        }
    }
}

@Composable
private fun TaskPositionSelector(
    selectedIndex: Int,
    positionCount: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(positionCount) { index ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(5.dp)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.claude.accent
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .then(
                        if (selected) Modifier
                        else Modifier.border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape,
                        ),
                    )
                    .clickable(enabled = enabled && !selected) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * 「进行中」只是状态展示，不再是可点的操作。
 *
 * 原先这里还能切换到「部分完成」，但那个档位已经取消；
 * 长按计时按钮是完成操作，状态标签再做成按钮只会让语义重复。
 */
@Composable
private fun InProgressBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val accent = MaterialTheme.claude.accent
    Surface(
        modifier = modifier.padding(start = 6.dp),
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = "进行中",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}
