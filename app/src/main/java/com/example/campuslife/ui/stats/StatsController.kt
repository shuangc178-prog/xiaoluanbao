package com.example.campuslife.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.toMinuteOfDay
import com.example.campuslife.data.plan.DailyPlan
import java.time.ZonedDateTime

/**
 * 统计页的全部交互状态。
 *
 * 打卡与计划由外面的控制器传进来（同一份数据不要读两遍）。
 * 统计页只关心打卡趋势与打卡日对应的计划快照，不再订阅日记或学习资料。
 */
class StatsController(
    val state: StatsUiState,
    val onSelectRange: (StatsRange) -> Unit,
    val onSelectDay: (Long) -> Unit,
    val onDismissDayDetail: () -> Unit,
    /** 用一张休息卡补上 [epochDay]（起止时刻由打卡控制器按平均值写入）。 */
    val onUseRestCard: (Long) -> Unit = {},
)

@Composable
fun rememberStatsController(
    records: List<CheckInRecord>,
    todayEpochDay: Long,
    plans: List<DailyPlan>,
    onUseRestCard: (Long) -> Unit = {},
    /** 卡册（抽卡收集来的），只喂给收集分区。 */
    ownedCards: CardCollection = CardCollection.Empty,
    /** 整套兑换换来的休息卡张数，要算进库存。 */
    bonusRestCards: Int = 0,
): StatsController {
    var rangeName by rememberSaveable { mutableStateOf(DefaultStatsRange.name) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(NoDaySelected) }

    val range = StatsRange.entries.firstOrNull { it.name == rangeName } ?: DefaultStatsRange

    val state = remember(
        records,
        todayEpochDay,
        range,
        plans,
        selectedEpochDay,
        ownedCards,
        bonusRestCards,
    ) {
        buildStatsUiState(
            records = records,
            todayEpochDay = todayEpochDay,
            range = range,
            plans = plans,
            selectedEpochDay = selectedEpochDay.takeIf { it != NoDaySelected },
            // 甘特图里「还在进行」的那一段要画到此刻；只影响画法，不参与任何汇总数字。
            nowMinuteOfDay = ZonedDateTime.now().toMinuteOfDay(),
            ownedCards = ownedCards,
            bonusRestCards = bonusRestCards,
        )
    }

    return StatsController(
        state = state,
        onSelectRange = { selected -> rangeName = selected.name },
        onSelectDay = { epochDay -> selectedEpochDay = epochDay },
        onDismissDayDetail = { selectedEpochDay = NoDaySelected },
        onUseRestCard = onUseRestCard,
    )
}

/** rememberSaveable 不接受可空 Long，用一个不可能的日期表示「没有选中」。 */
private const val NoDaySelected = Long.MIN_VALUE
