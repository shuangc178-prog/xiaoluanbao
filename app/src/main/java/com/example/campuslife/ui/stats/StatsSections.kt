package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.claude

/**
 * 统计页的各个分区。
 *
 * 与 [StatsScreen] 分开：屏幕文件只负责骨架与滚动顺序，
 * 这里堆具体卡片，避免页面文件无限膨胀。
 */

@Composable
internal fun StatsSectionTitle(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = supporting,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StreakCard(
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.claude.accent,
                )
                Text(
                    text = "连续打卡",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.currentStreakDays.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "天",
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InlineMetric(
                    caption = "最长连续",
                    value = "${state.longestStreakDays} 天",
                    modifier = Modifier.weight(1f),
                )
                InlineMetric(
                    caption = "累计完成",
                    value = "${state.completedDays} 天",
                    modifier = Modifier.weight(1f),
                )
                InlineMetric(
                    caption = "累计时长",
                    value = state.totalDurationLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InlineMetric(
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
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun CheckInTimeSection(
    state: StatsUiState,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
    onDayClick: ((Int) -> Unit)? = null,
) {
    val axis = ganttAxisFor(state.days)

    Column(modifier = modifier.fillMaxWidth()) {
        StatsSectionTitle(
            title = "开始 / 结束打卡时间",
            supporting = "每天一条横杠 · 横轴为一天内的时刻 · ${state.rangeLabel} · 点某一天看详情",
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            CheckInGanttChart(
                days = state.days,
                barColor = MaterialTheme.claude.accent,
                axis = axis,
                modifier = Modifier.padding(start = 8.dp, top = 14.dp, end = 14.dp, bottom = 12.dp),
                selectedIndex = selectedIndex,
                onDayClick = onDayClick,
            )
        }

        SummaryMetrics(
            summary = state.startSummary,
            accent = MaterialTheme.claude.accent,
            modifier = Modifier.padding(top = 12.dp),
        )
        SummaryMetrics(
            summary = state.endSummary,
            accent = MaterialTheme.claude.blue,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun CheckInDurationSection(
    state: StatsUiState,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
    onDayClick: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StatsSectionTitle(
            title = "每日打卡时长",
            supporting = "结束打卡 − 开始打卡 · ${state.rangeLabel}共 ${state.windowDurationLabel}",
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            DurationBarChart(
                dayLabels = state.dayLabels,
                values = state.durationMinutes,
                barColor = MaterialTheme.claude.green,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, end = 14.dp, bottom = 12.dp),
                selectedIndex = selectedIndex,
                onDayClick = onDayClick,
            )
        }

        SummaryMetrics(
            summary = state.durationSummary,
            accent = MaterialTheme.claude.green,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SummaryMetrics(
    summary: StatSummaryUiModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCell(
                    caption = summary.minCaption,
                    value = summary.minValue,
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    caption = summary.maxCaption,
                    value = summary.maxValue,
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    caption = summary.averageLabel,
                    value = summary.averageValue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
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
internal fun EmptyStatsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "还没有可统计的打卡记录",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 有打卡历史，但选中的区间里一条都没有：提示换个范围，而不是画一张空图。 */
@Composable
internal fun EmptyWindowNotice(
    rangeLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$rangeLabel 内没有打卡记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
