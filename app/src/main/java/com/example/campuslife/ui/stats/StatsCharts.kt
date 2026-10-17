package com.example.campuslife.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.ui.theme.claude
import java.time.LocalDate

internal fun learningBarFraction(durationMillis: Long, maximumMillis: Long): Float =
    if (maximumMillis <= 0L) 0f else
        (durationMillis.toDouble() / maximumMillis).toFloat().coerceIn(0f, 1f)

/** 初次展示或切换日期时从零增长；实时计时只更新目标值，不重播入场。 */
@Composable
private fun animatedBarFraction(target: Float, animationKey: Any): State<Float> {
    val fraction = remember(animationKey) { Animatable(0f) }
    LaunchedEffect(fraction, target) {
        fraction.animateTo(target, tween(durationMillis = 850, easing = FastOutSlowInEasing))
    }
    // Compose 的动画时钟会遵守系统的动画时长设置（含关闭动画）。
    return fraction.asState()
}

/** 一项任务一条实心横柱，同一尺度；不画余下的空轨道，避免暗示完成进度。 */
@Composable
internal fun TaskLearningChart(day: StatsDayUiModel, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("学习总时长", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(day.dateLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(day.durationLabel, style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.claude.green, modifier = Modifier.padding(top = 6.dp))
            }
            if (day.tasks.isEmpty()) {
                Text("这天没有安排任务", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val maximum = day.tasks.maxOfOrNull { it.learningDurationMillis } ?: 0L
            day.tasks.forEach { task ->
                key(day.epochDay, task.title) {
                    val fraction by animatedBarFraction(
                        learningBarFraction(task.learningDurationMillis, maximum), day.epochDay)
                    val color = when (task.learningPhase) {
                        TaskLearningPhase.Paused -> MaterialTheme.claude.danger
                        TaskLearningPhase.Idle -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.claude.green
                    }
                    Column(Modifier.semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(task.title, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                            Text(task.durationLabel, style = MaterialTheme.typography.labelLarge, color = color)
                        }
                        Box(Modifier.fillMaxWidth().height(10.dp)) {
                            if (fraction > 0f) {
                                Box(Modifier.fillMaxWidth(fraction).height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)).background(color))
                            }
                        }
                        val label = when (task.learningPhase) {
                            TaskLearningPhase.Idle -> "未开始"
                            TaskLearningPhase.Running -> "计时中"
                            TaskLearningPhase.Paused -> "已暂停"
                            TaskLearningPhase.Finished -> "已完成"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecentLearningChart(
    days: List<StatsDayUiModel>,
    selectedEpochDay: Long,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("近 7 天学习时长", style = MaterialTheme.typography.titleMedium)
            Text("点按日期柱查看任务用时", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val maximum = days.maxOfOrNull { it.learningDurationMillis } ?: 0L
            // 每个日期始终保留可点区域；窄屏或大字体下横向滚动，不压缩点击区域。
            val minimumWidth = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnWidth = maxOf(minimumWidth, maxWidth / 7)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    days.forEach { day ->
                        key(day.epochDay) {
                            val fraction by animatedBarFraction(
                                learningBarFraction(day.learningDurationMillis, maximum), day.epochDay)
                            val selected = day.epochDay == selectedEpochDay
                            val color = if (selected) MaterialTheme.claude.accent else MaterialTheme.claude.green
                            val date = LocalDate.ofEpochDay(day.epochDay)
                            Column(
                                modifier = Modifier.width(columnWidth)
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(selected = selected, role = Role.Button,
                                        onClick = { onSelectDay(day.epochDay) })
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val minutes = day.learningDurationMillis / 60_000L
                                val duration = if (minutes > 0L || day.learningDurationMillis == 0L) "${minutes}分"
                                    else "${day.learningDurationMillis / 1_000L}秒"
                                Text(duration, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics {
                                        contentDescription = "学习时长 ${day.durationLabel}"
                                    })
                                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.BottomCenter) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    if (fraction > 0f) {
                                        Box(Modifier.width(24.dp).fillMaxHeight(fraction)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(color))
                                    }
                                }
                                Text("${date.monthValue}/${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { contentDescription = day.dateLabel })
                                Text(if (day.isToday) "今天" else "", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (maximum == 0L) {
                Text("近 7 天还没有学习计时记录", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
