package com.example.campuslife.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.campuslife.R
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.ui.components.ClaudeButton
import com.example.campuslife.ui.components.ClaudeTextButton
import com.example.campuslife.ui.stats.CardCollectionSection
import com.example.campuslife.ui.stats.RestCardSection
import com.example.campuslife.ui.stats.StatsHistoryPicker
import com.example.campuslife.ui.stats.StatsDayUiModel
import com.example.campuslife.ui.stats.StatsUiState
import com.example.campuslife.ui.theme.claude

internal enum class TodayRewardPanel { RestCards, Collection }

/** 常驻的只有两个图案按钮，库存、卡面和兑换等详情按需打开。 */
@Composable
internal fun TodayRewardButtons(
    state: StatsUiState,
    onOpen: (TodayRewardPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayRewardPanel.entries.forEach { panel ->
            Surface(
                onClick = { onOpen(panel) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics {
                    stateDescription = when (panel) {
                        TodayRewardPanel.RestCards -> "${state.restCards.available} 张可用"
                        TodayRewardPanel.Collection -> "已收集 ${state.collections.sumOf { it.ownedKinds }} 种"
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (panel == TodayRewardPanel.RestCards) {
                        Image(painterResource(R.drawable.rest_card_chip), contentDescription = null,
                            modifier = Modifier.size(32.dp))
                    } else {
                        Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null,
                            tint = MaterialTheme.claude.accent, modifier = Modifier.size(28.dp))
                    }
                    Text(if (panel == TodayRewardPanel.RestCards) "休息卡" else "卡片收集",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 状态由路由持有，不随首页按钮滚出屏幕而丢失。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayRewardSheet(
    panel: TodayRewardPanel,
    state: StatsUiState,
    onDismiss: () -> Unit,
    dayDetails: (Long) -> StatsDayUiModel,
    onUseRestCard: (Long) -> Unit,
    onExchangeCards: (CardSeries) -> Unit,
) {
    var showDatePicker by rememberSaveable(panel) { mutableStateOf(false) }
    var selectedRestEpochDay by rememberSaveable(state.todayEpochDay) { mutableStateOf<Long?>(null) }
    val selectedDay = selectedRestEpochDay?.let(dayDetails)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (panel == TodayRewardPanel.RestCards) "休息卡" else "卡片收集",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ClaudeTextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
            when (panel) {
                TodayRewardPanel.RestCards -> {
                    item {
                        ClaudeTextButton(onClick = { showDatePicker = true }) {
                            Text("选择补记日期")
                        }
                        Text(selectedDay?.dateLabel ?: "可选择过去未打卡的日期补记",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selectedDay != null) {
                        item {
                            when {
                                selectedDay.isRestCardDay -> Text("这一天已用休息卡补记")
                                selectedDay.isCheckedIn -> Text("这一天已打卡，无需补记")
                                selectedDay.canUseRestCard -> ClaudeButton(
                                    onClick = { onUseRestCard(selectedDay.epochDay) },
                                    enabled = state.restCards.available > 0,
                                ) {
                                    Text(if (state.restCards.available > 0) "使用 1 张休息卡补记"
                                        else "暂无可用休息卡")
                                }
                            }
                        }
                    }
                    item { RestCardSection(state) }
                }
                TodayRewardPanel.Collection -> item {
                    CardCollectionSection(state.collections, onExchange = onExchangeCards)
                }
            }
        }
    }
    if (showDatePicker) {
        StatsHistoryPicker(
            selectedEpochDay = selectedRestEpochDay ?: state.todayEpochDay - 1,
            latestEpochDay = state.todayEpochDay - 1,
            onDismiss = { showDatePicker = false },
            onSelect = { selectedRestEpochDay = it; showDatePicker = false },
        )
    }
}
