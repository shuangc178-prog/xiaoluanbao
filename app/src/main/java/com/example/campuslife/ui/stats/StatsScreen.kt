package com.example.campuslife.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.EXCHANGE_REWARD_REST_CARDS
import com.example.campuslife.ui.theme.CampusLifeTheme

/**
 * 「统计」页：只回顾真实打卡记录与打卡日对应的计划完成情况。
 *
 * 页面只消费 [StatsUiState] 与几个回调，不碰数据层，方便脱离设备渲染 Preview。
 * 时间范围与被点开的那一天由 [StatsController] 持有，这样切 Tab 回来时还能保持住。
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier,
    onSelectRange: (StatsRange) -> Unit = {},
    onSelectDay: (Long) -> Unit = {},
    onDismissDayDetail: () -> Unit = {},
    onUseRestCard: (Long) -> Unit = {},
    /** 整套收集卡兑换休息卡。 */
    onExchangeCards: (CardSeries) -> Unit = {},
    showExchangeResult: Boolean = false,
    onDismissExchangeResult: () -> Unit = {},
    /** 是否展示「获得休息卡」的动效。由外面在卡数增加时置位。 */
    onDismissRestCardReveal: () -> Unit = {},
    showRestCardReveal: Boolean = false,
) {
    var isCardCollectionExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                StatsHeader(
                    rangeLabel = state.rangeLabel,
                    selectedRange = state.range,
                    onSelectRange = onSelectRange,
                )
            }

            if (state.completedDays > 0) {
                item(key = "streak") {
                    StreakCard(state = state)
                }
            }

            // 收集卡片与休息卡都是奖励，放在一起；所有弹共用一个默认收起的面板，
            // 新增卡池不会继续撑长统计页。
            if (state.collections.isNotEmpty()) {
                item(key = "card-collections") {
                    CardCollectionFold(
                        collections = state.collections,
                        expanded = isCardCollectionExpanded,
                        onExpandedChange = { isCardCollectionExpanded = it },
                        onExchange = onExchangeCards,
                    )
                }
            }

            // 奖励分区放在成就卡之后、图表之前：它是「怎么用」的说明，
            // 而用法指向下面的图表（点某一天）。
            if (state.completedDays > 0) {
                item(key = "rest-card") {
                    RestCardSection(state = state)
                }
            }

            val selectedIndex = state.selectedDay
                ?.let { selected -> state.days.indexOfFirst { it.epochDay == selected.epochDay } }
                ?.takeIf { it >= 0 }
            val onDayClick: (Int) -> Unit = { index ->
                state.days.getOrNull(index)?.let { day -> onSelectDay(day.epochDay) }
            }

            when {
                state.days.isEmpty() -> item(key = "empty") {
                    EmptyStatsCard()
                }

                !state.hasChartData -> item(key = "empty-window") {
                    EmptyWindowNotice(rangeLabel = state.rangeLabel)
                }

                else -> {
                    item(key = "time-chart") {
                        CheckInTimeSection(
                            state = state,
                            selectedIndex = selectedIndex,
                            onDayClick = onDayClick,
                        )
                    }

                    item(key = "duration-chart") {
                        CheckInDurationSection(
                            state = state,
                            selectedIndex = selectedIndex,
                            onDayClick = onDayClick,
                        )
                    }
                }
            }
        }
    }

    state.selectedDay?.let { day ->
        StatsDayDetailSheet(
            day = day,
            availableRestCards = state.restCards.available,
            onDismiss = onDismissDayDetail,
            onUseRestCard = { onUseRestCard(day.epochDay) },
        )
    }

    if (showRestCardReveal) {
        RestCardReveal(onDismiss = onDismissRestCardReveal)
    }

    if (showExchangeResult) {
        CardExchangeResultDialog(
            rewardRestCards = state.collections.firstOrNull()?.rewardRestCards
                ?: EXCHANGE_REWARD_REST_CARDS,
            onDismiss = onDismissExchangeResult,
        )
    }
}

@Composable
private fun StatsHeader(
    rangeLabel: String,
    selectedRange: StatsRange,
    onSelectRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "统计",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "真实打卡趋势 · $rangeLabel",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatsRangeChips(
            selectedRange = selectedRange,
            onSelectRange = onSelectRange,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * 时间范围切换。
 *
 * 只影响回顾性的图表与列表，不影响连续打卡与累计天数——
 * 那两个数字是「历史总共」，被区间切来切去反而看不懂。
 */
@Composable
private fun StatsRangeChips(
    selectedRange: StatsRange,
    onSelectRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { range ->
            val selected = range == selectedRange
            FilterChip(
                selected = selected,
                onClick = { onSelectRange(range) },
                label = { Text(range.label) },
                shape = RoundedCornerShape(12.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Preview(
    name = "Card collection",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenCardCollectionPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.withCards)
    }
}

@Preview(
    name = "Card collection · complete",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenCardCollectionCompletePreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.withCompleteCards)
    }
}

@Preview(
    name = "Card collection · complete · dark",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenCardCollectionCompleteDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        StatsScreen(state = StatsPreviewData.withCompleteCards)
    }
}

@Preview(
    name = "Rest cards",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenRestCardsPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.withRestCards)
    }
}

@Preview(
    name = "Rest cards · dark",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenRestCardsDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        StatsScreen(state = StatsPreviewData.withRestCards)
    }
}

@Preview(
    name = "Populated",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.populated)
    }
}

@Preview(
    name = "Week range",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1800,
)
@Composable
fun StatsScreenWeekPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.week)
    }
}

@Preview(
    name = "Month range",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1800,
)
@Composable
fun StatsScreenMonthPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.month)
    }
}

@Preview(
    name = "Sparse records",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1800,
)
@Composable
fun StatsScreenSparsePreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.sparse)
    }
}

@Preview(
    name = "Plans only",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1400,
)
@Composable
fun StatsScreenPlansOnlyPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.plansOnly)
    }
}

@Preview(
    name = "Stale window",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1400,
)
@Composable
fun StatsScreenStaleWindowPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.staleWindow)
    }
}

@Preview(
    name = "Empty",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 1000,
)
@Composable
fun StatsScreenEmptyPreview() {
    CampusLifeTheme(darkTheme = false) {
        StatsScreen(state = StatsPreviewData.empty)
    }
}

@Preview(
    name = "Dark",
    group = "Stats",
    showBackground = true,
    widthDp = 412,
    heightDp = 2200,
)
@Composable
fun StatsScreenDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        StatsScreen(state = StatsPreviewData.populated)
    }
}
