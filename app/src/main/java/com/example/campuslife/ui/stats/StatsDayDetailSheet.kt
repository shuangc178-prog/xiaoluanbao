package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.theme.claude

/**
 * 点开某一天后的详情弹层：打卡起止、时长与当天任务。
 *
 * 只展示，不提供修改入口——统计页是回顾性的页面，回改数据要去本日计划。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsDayDetailSheet(
    day: StatsDayUiModel,
    availableRestCards: Int,
    onDismiss: () -> Unit,
    onUseRestCard: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DayDetailContent(
            day = day,
            availableRestCards = availableRestCards,
            onUseRestCard = onUseRestCard,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        )
    }
}

@Composable
internal fun DayDetailContent(
    day: StatsDayUiModel,
    modifier: Modifier = Modifier,
    availableRestCards: Int = 0,
    onUseRestCard: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = day.dateLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (day.isToday) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "今天",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (day.isRestCardDay) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.claude.accentSoft,
                    contentColor = MaterialTheme.claude.accent,
                ) {
                    Text(
                        text = "休息卡补记",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (day.hasCheckIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailMetric(
                    caption = "开始打卡",
                    value = day.startLabel ?: "--:--",
                    modifier = Modifier.weight(1f),
                )
                DetailMetric(
                    caption = "结束打卡",
                    value = day.endLabel ?: "进行中",
                    modifier = Modifier.weight(1f),
                )
                DetailMetric(
                    caption = "打卡时长",
                    value = day.durationLabel ?: "--",
                    modifier = Modifier.weight(1f),
                )
            }
            if (day.isRestCardDay) {
                InfoRow(
                    text = "这一天的起止时刻是休息卡按你的平均打卡时间写入的，不是你当天真实的起止。" +
                        "它计进累计天数，但不计进「连续满 5 天」的奖励进度。",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            InfoRow(
                text = "这一天没有打卡记录。",
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        if (day.canUseRestCard) {
            RestCardUseBlock(
                available = availableRestCards,
                onUseRestCard = onUseRestCard,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        DetailBlock(title = "当天任务", supporting = dayTaskSupporting(day)) {
            if (day.tasks.isEmpty()) {
                Text(
                    text = "这天没有留下计划快照。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.tasks.forEach { task ->
                    DayDetailTaskRow(
                        task = task,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 「用休息卡补上这一天」。
 *
 * 没有库存时按钮禁用并说明原因，而不是把入口藏起来——
 * 用户需要知道「这东西存在、怎么才能有」。
 */
@Composable
private fun RestCardUseBlock(
    available: Int,
    onUseRestCard: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RestCardChip(width = 52.dp, revealProgress = if (available > 0) 1f else 0.35f)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "用休息卡补上这一天",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (available > 0) {
                        "可用 $available 张 · 起止时刻按你的平均打卡时间写入"
                    } else {
                        "还没有可用的休息卡。连续打卡满 5 天得一张。"
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onUseRestCard?.invoke() },
                enabled = available > 0 && onUseRestCard != null,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("使用")
            }
        }
    }
}

private fun dayTaskSupporting(day: StatsDayUiModel): String? = when {
    day.plannedCount <= 0 -> null
    else -> "完成 ${day.completedCount} / ${day.plannedCount}"
}

@Composable
private fun DetailBlock(
    title: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun DetailMetric(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InfoRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayDetailTaskRow(
    task: StatsTaskUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (task.isCompleted) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
            tint = if (task.isCompleted) {
                MaterialTheme.claude.green
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (task.supportingText.isNotBlank()) {
                Text(
                    text = task.supportingText,
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(
    name = "Day detail",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
fun DayDetailContentPreview() {
    CampusLifeTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DayDetailContent(
                day = StatsPreviewData.selectedDay,
                availableRestCards = 2,
                onUseRestCard = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Preview(
    name = "Day detail · no rest card",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
fun DayDetailContentNoRestCardPreview() {
    CampusLifeTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DayDetailContent(
                day = StatsPreviewData.emptyDay,
                availableRestCards = 0,
                onUseRestCard = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Preview(
    name = "Day detail · filled by rest card",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
fun DayDetailContentRestCardDayPreview() {
    CampusLifeTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DayDetailContent(
                day = StatsPreviewData.restCardDay,
                availableRestCards = 1,
                onUseRestCard = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Preview(
    name = "Rest card reveal",
    group = "Stats",
    showBackground = false,
    widthDp = 412,
    heightDp = 820,
)
@Composable
fun RestCardRevealPreview() {
    CampusLifeTheme(darkTheme = false) {
        RestCardReveal(onDismiss = {})
    }
}

@Preview(
    name = "Rest card reveal · dark",
    group = "Stats",
    showBackground = false,
    widthDp = 412,
    heightDp = 820,
)
@Composable
fun RestCardRevealDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        RestCardReveal(onDismiss = {})
    }
}
