package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.checkin.REST_CARD_STREAK_DAYS
import com.example.campuslife.ui.theme.claude

/**
 * 统计页的「休息卡」分区：奖励从哪里来、现在有几张、还差几天、过去的卡去了哪。
 *
 * 这一块是统计页上唯一的**非统计**内容，但它必须放在这里——
 * 休息卡的使用入口是「点某一天」，而点某一天只发生在统计页的图表上。
 */
@Composable
internal fun RestCardSection(
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    val restCards = state.restCards

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RestCardChip(width = 68.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "休息卡",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = restCardProgressLabel(restCards),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.claude.accent,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${restCards.available}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "张可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RestCardProgressBar(
                progressDays = restCards.progressDays,
                modifier = Modifier.padding(top = 16.dp),
            )


            if (state.restCardGrants.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "已获得的卡",
                    modifier = Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.restCardGrants.forEachIndexed { index, grant ->
                    RestCardGrantRow(
                        grant = grant,
                        modifier = Modifier.padding(top = if (index == 0) 12.dp else 10.dp),
                    )
                }
            }
        }
    }
}

/** 「再坚持 N 天得下一张」。 */
internal fun restCardProgressLabel(restCards: RestCardUiState): String = when {
    restCards.earned == 0 && restCards.progressDays == 0 ->
        "从今天开始算：连续打卡 $REST_CARD_STREAK_DAYS 天得第一张"
    restCards.progressDays == 0 ->
        "这一轮刚开始，连续打卡 $REST_CARD_STREAK_DAYS 天得下一张"
    else ->
        "当前连续 ${restCards.progressDays} 天 · 再坚持 ${restCards.daysToNext} 天得下一张"
}

/** 进度条：一轮 5 天，攒满就发卡。 */
@Composable
private fun RestCardProgressBar(
    progressDays: Int,
    modifier: Modifier = Modifier,
) {
    val filled = progressDays % REST_CARD_STREAK_DAYS
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(REST_CARD_STREAK_DAYS) { index ->
            val isFilled = index < filled
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                shape = RoundedCornerShape(999.dp),
                color = if (isFilled) {
                    MaterialTheme.claude.accent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                content = {},
            )
        }
    }
}

/** 卡槽里的小卡面 + 一行去向说明（已用 / 未使用）。 */
@Composable
internal fun RestCardGrantRow(
    grant: RestCardGrantUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RestCardChip(width = 44.dp, revealProgress = if (grant.isUsed) 0.45f else 1f)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (grant.isUsed) "已用于 ${grant.usedDateLabel}" else "未使用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = grant.earnedDateLabel,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
