package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.ui.theme.claude

internal fun learningBarFraction(durationMillis: Long, maximumMillis: Long): Float =
    if (maximumMillis <= 0L) 0f else
        (durationMillis.toDouble() / maximumMillis).toFloat().coerceIn(0f, 1f)

/** 一项任务一条横柱，同一尺度；未计时任务保留空轨道。 */
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
                Text(day.durationLabel, style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.claude.green, modifier = Modifier.padding(top = 6.dp))
            }
            if (day.tasks.isEmpty()) {
                Text("这天没有安排任务", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val maximum = day.tasks.maxOfOrNull { it.learningDurationMillis } ?: 0L
            day.tasks.forEach { task ->
                val color = when (task.learningPhase) {
                    TaskLearningPhase.Paused -> MaterialTheme.claude.danger
                    TaskLearningPhase.Idle -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.claude.green
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(task.title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                        Text(task.durationLabel, style = MaterialTheme.typography.labelLarge, color = color)
                    }
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val fraction = learningBarFraction(task.learningDurationMillis, maximum)
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
