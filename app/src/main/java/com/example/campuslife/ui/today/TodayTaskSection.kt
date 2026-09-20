package com.example.campuslife.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * 勾选与否以 [completedTitles] 为准——它是当天计划存档里的完成状态，
 * 而不是 [TodayPriorityUiModel.status]，这样勾选结果能跨 Tab、跨重启保留。
 *
 * [isLocked] 为 true 表示当天已结束打卡：**勾选锁住，不能再取消**。
 * 界面只做两件事——复选框禁用、「添加」收起——真正拦住写入的是 [DailyPlanController]。
 * 按第 16.25 节的口径不写解释句子，锁定状态靠状态字「已打卡」与禁用态表达。
 */
@Composable
internal fun TodayPrioritiesSection(
    priorities: List<TodayPriorityUiModel>,
    completedTitles: Set<String>,
    onPriorityClick: (String) -> Unit,
    onPriorityToggle: (String) -> Unit,
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
 * 这里刻意不放完成勾选：明日任务只是预排，到了次日凌晨 6 点成为任务栏后才能执行。
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
            TextButton(
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
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
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
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
            checked = isCompleted,
            // 已打卡：勾选锁住。给 null 而不是空实现，复选框才会呈现禁用态，
            // 用户一眼能看出这里点不动，而不是点了没反应。
            onCheckedChange = if (isLocked) null else { { onToggle() } },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.claude.accent,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
            )
            Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
            Text(
                text = priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )
            Text(
                text = priority.supportingText,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

        // 预期开始时间：安排了就显示钟点，没安排则留一句轻提示，告诉用户这里可以排时间。
            val startLabel = priority.expectedStartLabel
            if (startLabel != null) {
                TaskTimeChip(
                label = startLabel,
                modifier = Modifier.padding(start = 6.dp),
                )
            } else {
                Text(
                text = "未排时间",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InProgressBadge(visible = priority.status == TodayItemStatus.InProgress)
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
            TextButton(onClick = onUseAutomaticOrder, enabled = enabled) {
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
 * 勾选是唯一的完成操作，状态标签再做成按钮只会让语义重复。
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
