package com.example.campuslife.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.plan.DailyPlan

class StatsController(
    val state: StatsUiState,
    val onSelectDay: (Long) -> Unit,
    val onShowToday: () -> Unit,
    val onUseRestCard: (Long) -> Unit = {},
    /** 只读取某日明细，不改变统计页当前选择，供休息卡弹层选日期。 */
    val dayDetails: (Long) -> StatsDayUiModel = { state.day },
)

@Composable
fun rememberStatsController(
    records: List<CheckInRecord>,
    todayEpochDay: Long,
    plans: List<DailyPlan>,
    nowEpochMillis: Long,
    onUseRestCard: (Long) -> Unit = {},
    ownedCards: CardCollection = CardCollection.Empty,
    bonusRestCards: Int = 0,
): StatsController {
    // null 表示跟随今天；跨日后仍然自动显示新的一天。历史选择则固定为所选日。
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    fun stateForDay(epochDay: Long?) = buildStatsUiState(
        records = records,
        todayEpochDay = todayEpochDay,
        plans = plans,
        selectedEpochDay = epochDay,
        nowEpochMillis = nowEpochMillis,
        ownedCards = ownedCards,
        bonusRestCards = bonusRestCards,
    )
    val state = stateForDay(selectedEpochDay)
    return StatsController(
        state = state,
        onSelectDay = { selectedEpochDay = it.coerceAtMost(todayEpochDay) },
        onShowToday = { selectedEpochDay = null },
        onUseRestCard = onUseRestCard,
        dayDetails = { epochDay ->
            state.recentDays.firstOrNull { it.epochDay == epochDay } ?: stateForDay(epochDay).day
        },
    )
}
