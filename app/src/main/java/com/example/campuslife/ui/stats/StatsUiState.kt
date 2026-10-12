package com.example.campuslife.ui.stats

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.EXCHANGE_REWARD_REST_CARDS
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.data.card.MAX_COPIES_PER_CARD
import com.example.campuslife.data.card.canExchangeCards
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.REST_CARD_STREAK_DAYS
import com.example.campuslife.data.checkin.completedCheckInDays
import com.example.campuslife.data.checkin.earnedRestCards
import com.example.campuslife.data.checkin.formatMinuteOfDay
import com.example.campuslife.data.checkin.realCheckInDays
import com.example.campuslife.data.checkin.restCardDays
import com.example.campuslife.data.checkin.restCardStreakProgress
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.data.plan.formatLearningDuration
import com.example.campuslife.data.plan.formatPlanDateLabel
import com.example.campuslife.data.plan.learningDurationMillis
import com.example.campuslife.data.plan.learningPhase
import com.example.campuslife.data.plan.learningDayEndMillis
import java.time.LocalDate

/** 学习时间仅来自任务手动计时，与打卡起止时刻、休息卡补记无关。 */
data class StatsTaskUiModel(
    val title: String,
    val supportingText: String,
    val isCompleted: Boolean,
    val learningDurationMillis: Long = 0L,
    val learningPhase: TaskLearningPhase = TaskLearningPhase.Idle,
) {
    val durationLabel: String get() = formatLearningDuration(learningDurationMillis)
}

data class StatsDayUiModel(
    val epochDay: Long,
    val dateLabel: String,
    val isToday: Boolean,
    val isCheckedIn: Boolean = false,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val tasks: List<StatsTaskUiModel> = emptyList(),
    val isRestCardDay: Boolean = false,
    val canUseRestCard: Boolean = false,
) {
    val plannedCount: Int get() = tasks.size
    val completedCount: Int get() = tasks.count { it.isCompleted }
    val hasCheckIn: Boolean get() = startMinute != null
    val startLabel: String? get() = startMinute?.let(::formatMinuteOfDay)
    val endLabel: String? get() = endMinute?.let(::formatMinuteOfDay)
    val learningDurationMillis: Long get() = tasks.sumOf { it.learningDurationMillis }
    val durationLabel: String get() = formatLearningDuration(learningDurationMillis)
}

/**
 * 休息卡的库存与进度。
 *
 * [available] = 已经发出来的张数 − 用掉的张数。打卡攒出来的那些是纯推导的
 * （见 [earnedRestCards]），所以库存不会与记录脱节；[fromExchange] 是用整套收集卡
 * 换来的，推导不出来，单独记账（见 `CheckInStore.bonusRestCards`）。
 */
data class RestCardUiState(
    val earned: Int = 0,
    val used: Int = 0,
    /** 当前这一轮真实连续打卡攒了几天，用于算「还差几天」。 */
    val progressDays: Int = 0,
    /** 最近一张卡的获得日，用于动效与文案；没有就是 null。 */
    val latestEarnedEpochDay: Long? = null,
    /** 其中有多少张来自「整套收集卡兑换」。 */
    val fromExchange: Int = 0,
) {
    val available: Int get() = (earned - used).coerceAtLeast(0)

    /** 距离下一张卡还差几天真实打卡（1..5）。 */
    val daysToNext: Int get() = REST_CARD_STREAK_DAYS - progressDays % REST_CARD_STREAK_DAYS
}

/** 卡册里的一格：一张卡 + 已经拿到几张。 */
data class CardSlotUiModel(
    val card: GachaCard,
    val owned: Int,
    val maxCopies: Int,
) {
    val isOwned: Boolean get() = owned > 0
    val isFull: Boolean get() = owned >= maxCopies
}

/**
 * 单个抽卡系列的收集进度。
 *
 * 卡册本身是一份纯数据（每种卡几张），这里只是它给统计页看的形状。
 */
data class CardCollectionUiState(
    val series: CardSeries = CardSeries.Isekai,
    val slots: List<CardSlotUiModel> = emptyList(),
    val ownedKinds: Int = 0,
    val setSize: Int = 0,
    val totalCount: Int = 0,
    val canExchange: Boolean = false,
    /** 兑换能换到几张休息卡。 */
    val rewardRestCards: Int = 0,
) {
    val isEmpty: Boolean get() = totalCount == 0

    /** 整套集齐。 */
    val isComplete: Boolean get() = setSize > 0 && ownedKinds >= setSize
}

/** 统计页上的奖励分区：一条已经拿到的卡。 */
data class RestCardGrantUiModel(
    val earnedEpochDay: Long,
    val earnedDateLabel: String,
    /** 这张卡已经用掉了（按下标从最早的卡开始核销）。 */
    val isUsed: Boolean,
    /** 这张卡用在了哪一天，null 表示还没用。 */
    val usedEpochDay: Long? = null,
    val usedDateLabel: String? = null,
)

data class StatsUiState(
    val todayEpochDay: Long,
    val day: StatsDayUiModel,
    val completedDays: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalDurationLabel: String = "00:00",
    val restCards: RestCardUiState = RestCardUiState(),
    val collections: List<CardCollectionUiState> = emptyList(),
    val restCardGrants: List<RestCardGrantUiModel> = emptyList(),
) {
    val collection: CardCollectionUiState
        get() = collections.firstOrNull { it.series == CardSeries.Isekai } ?: CardCollectionUiState()
}

/** 仅展示所选一天。历史全量数据只用于连续打卡、累计学习时长与奖励库存。 */
fun buildStatsUiState(
    records: List<CheckInRecord>,
    todayEpochDay: Long,
    plans: List<DailyPlan> = emptyList(),
    selectedEpochDay: Long? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
    ownedCards: CardCollection = CardCollection.Empty,
    bonusRestCards: Int = 0,
): StatsUiState {
    val completedDays = completedCheckInDays(records, plans, todayEpochDay)
    val realDays = realCheckInDays(records, plans, todayEpochDay)
    val restDays = restCardDays(records)
    val grants = earnedRestCards(realDays)
    val usedDays = restDays.sorted()
    val selected = (selectedEpochDay ?: todayEpochDay).coerceAtMost(todayEpochDay)
    val record = records.firstOrNull { it.epochDay == selected }
    val plan = plans.firstOrNull { it.epochDay == selected }
    val date = LocalDate.ofEpochDay(selected)
    val day = StatsDayUiModel(
        epochDay = selected,
        dateLabel = formatPlanDateLabel(date),
        isToday = selected == todayEpochDay,
        isCheckedIn = selected in completedDays,
        startMinute = record?.startMinuteOfDay,
        endMinute = record?.endMinuteOfDay,
        tasks = plan?.orderedTasks?.map { task ->
            StatsTaskUiModel(
                title = task.title,
                supportingText = task.supportingText,
                isCompleted = task.isCompleted,
                learningDurationMillis = task.learningDurationMillis(selected, nowEpochMillis),
                learningPhase = if (task.learningPhase == TaskLearningPhase.Running &&
                    nowEpochMillis >= learningDayEndMillis(selected)) TaskLearningPhase.Paused
                    else task.learningPhase,
            )
        }.orEmpty(),
        isRestCardDay = selected in restDays,
        canUseRestCard = selected < todayEpochDay && selected !in completedDays && selected !in restDays,
    )
    val allLearningMillis = plans.filter { it.epochDay <= todayEpochDay }.sumOf { history ->
        history.tasks.sumOf { it.learningDurationMillis(history.epochDay, nowEpochMillis) }
    }
    return StatsUiState(
        todayEpochDay = todayEpochDay,
        day = day,
        completedDays = completedDays.size,
        currentStreakDays = currentStreak(completedDays.toList(), todayEpochDay),
        longestStreakDays = longestStreak(completedDays.toList()),
        totalDurationLabel = formatLearningDuration(allLearningMillis),
        restCards = RestCardUiState(
            earned = grants.size + bonusRestCards,
            used = usedDays.size,
            progressDays = restCardStreakProgress(realDays, todayEpochDay),
            latestEarnedEpochDay = grants.lastOrNull()?.earnedEpochDay,
            fromExchange = bonusRestCards,
        ),
        restCardGrants = grants.mapIndexed { index, grant ->
            val usedDay = usedDays.getOrNull(index)
            RestCardGrantUiModel(
                earnedEpochDay = grant.earnedEpochDay,
                earnedDateLabel = formatEpochDayLabel(grant.earnedEpochDay),
                isUsed = usedDay != null,
                usedEpochDay = usedDay,
                usedDateLabel = usedDay?.let(::formatEpochDayLabel),
            )
        }.reversed(),
        collections = buildCollectionUiStates(ownedCards),
    )
}

internal fun buildCollectionUiState(
    collection: CardCollection,
    series: CardSeries = CardSeries.Isekai,
): CardCollectionUiState =
    CardCollectionUiState(
        series = series,
        slots = series.cards.map { card ->
            CardSlotUiModel(
                card = card,
                owned = collection.countOf(card),
                maxCopies = MAX_COPIES_PER_CARD,
            )
        },
        ownedKinds = collection.ownedKinds(series),
        setSize = collection.setSize(series),
        totalCount = collection.totalCount(series),
        canExchange = canExchangeCards(collection, series),
        rewardRestCards = EXCHANGE_REWARD_REST_CARDS,
    )

internal fun buildCollectionUiStates(collection: CardCollection): List<CardCollectionUiState> =
    CardSeries.entries.sortedBy { it.number }.map { series ->
        buildCollectionUiState(collection, series)
    }

/** `2026-09-14` → `9月14日`。 */
private fun formatEpochDayLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return "${date.monthValue}月${date.dayOfMonth}日"
}

/** 当前连续打卡天数。今天还没打卡不算断，从今天往回数时会从前一天起算。 */
internal fun currentStreak(completedEpochDays: List<Long>, todayEpochDay: Long): Int {
    if (completedEpochDays.isEmpty()) return 0
    val days = completedEpochDays.toHashSet()
    var cursor = todayEpochDay
    if (cursor !in days) cursor -= 1
    var streak = 0
    while (cursor in days) {
        streak += 1
        cursor -= 1
    }
    return streak
}

internal fun longestStreak(completedEpochDays: List<Long>): Int {
    if (completedEpochDays.isEmpty()) return 0
    val sorted = completedEpochDays.distinct().sorted()
    var longest = 1
    var run = 1
    for (index in 1..sorted.lastIndex) {
        run = if (sorted[index] == sorted[index - 1] + 1) run + 1 else 1
        if (run > longest) longest = run
    }
    return longest
}
