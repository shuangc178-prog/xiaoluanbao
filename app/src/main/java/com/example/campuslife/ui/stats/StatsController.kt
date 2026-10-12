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
    return StatsController(
        state = buildStatsUiState(
            records = records,
            todayEpochDay = todayEpochDay,
            plans = plans,
            selectedEpochDay = selectedEpochDay,
            nowEpochMillis = nowEpochMillis,
            ownedCards = ownedCards,
            bonusRestCards = bonusRestCards,
        ),
        onSelectDay = { selectedEpochDay = it.coerceAtMost(todayEpochDay) },
        onShowToday = { selectedEpochDay = null },
        onUseRestCard = onUseRestCard,
    )
}
