package com.example.campuslife.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import com.example.campuslife.data.plan.formatPlanDateLabel
import com.example.campuslife.ui.reminder.TaskTimeChip
import com.example.campuslife.ui.theme.claude
import java.time.DayOfWeek
import java.time.LocalDate

private const val NoSelectedArchiveDay = Long.MIN_VALUE

internal data class PlanArchiveDayUiModel(
    val epochDay: Long,
    val date: LocalDate,
    val plan: DailyPlan?,
)

internal data class PlanArchiveWeekUiModel(
    val weekStartEpochDay: Long,
    val label: String,
    val days: List<PlanArchiveDayUiModel>,
) {
    val recordedDayCount: Int get() = days.count { it.plan != null }
}

/**
 * 每日计划存档页。
 *
 * 只按有记录的周建立分组，每周固定展示 7 个日期气泡；因此计划存档数量可以持续增长，
 * 但不会把所有任务直接堆成长列表。点击日期气泡后再进入那一天的只读存档详情。
 */
@Composable
fun DailyPlanArchiveScreen(
    plans: List<DailyPlan>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedEpochDay by rememberSaveable { mutableStateOf(NoSelectedArchiveDay) }
    val weeks = remember(plans) { buildPlanArchiveWeeks(plans) }

    if (selectedEpochDay != NoSelectedArchiveDay) {
        BackHandler { selectedEpochDay = NoSelectedArchiveDay }
        PlanArchiveDayScreen(
            date = LocalDate.ofEpochDay(selectedEpochDay),
            plan = plans.lastOrNull { it.epochDay == selectedEpochDay },
            onBackClick = { selectedEpochDay = NoSelectedArchiveDay },
            modifier = modifier,
        )
        return
    }

    BackHandler(onBack = onBackClick)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                ),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "archive-header") {
                ArchiveTopBar(
                    title = "每日计划存档",
                    supporting = "按周收纳 · 共 ${plans.size} 天有记录",
                    onBackClick = onBackClick,
                )
            }

            if (weeks.isEmpty()) {
                item(key = "archive-empty") {
                    EmptyArchiveCard()
                }
            } else {
                item(key = "archive-hint") {
                    Text(
                        text = "点击某一天的气泡，查看当天完整计划。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    items = weeks,
                    key = { it.weekStartEpochDay },
                ) { week ->
                    // 存档周默认全部收起，想看哪一周再展开，避免长列表一进来就铺满屏幕。
                    WeeklyArchiveSection(
                        week = week,
                        onDayClick = { selectedEpochDay = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyArchiveSection(
    week: PlanArchiveWeekUiModel,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(week.weekStartEpochDay) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = week.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${week.recordedDayCount} / 7 天有计划",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起本周" else "展开本周",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    week.days.forEach { day ->
                        PlanArchiveDayBubble(
                            day = day,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(day.epochDay) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanArchiveDayBubble(
    day: PlanArchiveDayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = day.plan
    val bubbleColor = when {
        plan == null -> MaterialTheme.colorScheme.surfaceVariant
        plan.isAllCompleted -> MaterialTheme.claude.greenSoft
        else -> MaterialTheme.claude.accentSoft
    }
    val borderColor = when {
        plan == null -> MaterialTheme.colorScheme.outlineVariant
        plan.isAllCompleted -> MaterialTheme.claude.green.copy(alpha = 0.35f)
        else -> MaterialTheme.claude.accent.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .semantics {
                contentDescription = "${formatPlanDateLabel(day.date)}计划存档"
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = bubbleColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = shortWeekday(day.date.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PlanArchiveDayScreen(
    date: LocalDate,
    plan: DailyPlan?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                ),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "day-header") {
                ArchiveTopBar(
                    title = "计划存档",
                    supporting = formatPlanDateLabel(date),
                    onBackClick = onBackClick,
                )
            }

            if (plan == null) {
                item(key = "day-empty") {
                    EmptyArchiveCard(
                        title = "这一天还没有计划存档",
                        supporting = "回到本日计划后添加任务，这里会自动保存当天快照。",
                    )
                }
            } else {
                item(key = "day-summary") {
                    PlanArchiveSummary(plan = plan)
                }
                if (plan.tasks.isEmpty()) {
                    item(key = "day-no-tasks") {
                        EmptyArchiveCard(
                            title = "当天没有任务",
                            supporting = "这一天的计划快照为空。",
                        )
                    }
                } else {
                    item(key = "day-tasks-title") {
                        Text(
                            text = "当天任务",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(
                        items = plan.orderedTasks,
                        key = { it.title },
                    ) { task ->
                        PlanArchiveTaskRow(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveTopBar(
    title: String,
    supporting: String,
    onBackClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = supporting,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanArchiveSummary(
    plan: DailyPlan,
    modifier: Modifier = Modifier,
) {
    val progress = if (plan.totalCount == 0) 0f else {
        plan.completedCount.toFloat() / plan.totalCount
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "完成进度",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${plan.completedCount} / ${plan.totalCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (plan.isAllCompleted) {
                        MaterialTheme.claude.green
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (plan.totalCount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(4.dp),
                    color = if (plan.isAllCompleted) {
                        MaterialTheme.claude.green
                    } else {
                        MaterialTheme.claude.accent
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanArchiveTaskRow(
    task: PlanTask,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (task.isCompleted) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = if (task.isCompleted) "已完成" else "未完成",
            modifier = Modifier.size(19.dp),
            tint = if (task.isCompleted) {
                MaterialTheme.claude.green
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
            )
            if (task.supportingText.isNotBlank()) {
                Text(
                    text = task.supportingText,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val startLabel = task.expectedStartLabel
        if (startLabel != null) {
            TaskTimeChip(label = startLabel)
        }
    }
}

@Composable
private fun EmptyArchiveCard(
    title: String = "还没有计划存档",
    supporting: String = "每天进入本日计划后，计划会自动保存为当天快照。",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun buildPlanArchiveWeeks(plans: List<DailyPlan>): List<PlanArchiveWeekUiModel> {
    if (plans.isEmpty()) return emptyList()

    val planByDay = plans.associateBy { it.epochDay }
    return plans
        .map { startOfWeek(it.date) }
        .distinct()
        .sortedDescending()
        .map { weekStart ->
            val days = (0L..6L).map { offset ->
                val date = weekStart.plusDays(offset)
                PlanArchiveDayUiModel(
                    epochDay = date.toEpochDay(),
                    date = date,
                    plan = planByDay[date.toEpochDay()],
                )
            }
            PlanArchiveWeekUiModel(
                weekStartEpochDay = weekStart.toEpochDay(),
                label = formatWeekLabel(weekStart),
                days = days,
            )
        }
}

private fun startOfWeek(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

private fun formatWeekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.year == end.year) {
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日—" +
            "${end.monthValue}月${end.dayOfMonth}日"
    } else {
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日—" +
            "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
    }
}

private fun shortWeekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
