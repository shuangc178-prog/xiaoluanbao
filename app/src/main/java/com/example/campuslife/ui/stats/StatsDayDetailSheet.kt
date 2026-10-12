package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.campuslife.ui.components.ClaudeButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.claude

/** 所选日的打卡状态与真实学习计时分别展示。 */
@Composable
internal fun DayCheckInCard(
    day: StatsDayUiModel,
    availableRestCards: Int,
    onUseRestCard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("当天打卡", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        day.isRestCardDay -> "休息卡补记"
                        day.isCheckedIn -> "已打卡"
                        day.hasCheckIn -> "未结束打卡"
                        else -> "未打卡"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (day.isCheckedIn) MaterialTheme.claude.green
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.hasCheckIn && !day.isRestCardDay) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("开始 ${day.startLabel}", style = MaterialTheme.typography.bodySmall)
                    Text("结束 ${day.endLabel ?: "--:--"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("任务完成 ${day.completedCount} / ${day.plannedCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (day.canUseRestCard) {
                ClaudeButton(onClick = onUseRestCard, enabled = availableRestCards > 0) {
                    Text(if (availableRestCards > 0) "用休息卡补记 · 剩余 $availableRestCards 张"
                        else "暂无可用休息卡")
                }
            }
        }
    }
}
