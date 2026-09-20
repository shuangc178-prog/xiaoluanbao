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
import com.example.campuslife.data.checkin.formatDurationLabel
import com.example.campuslife.data.checkin.formatMinuteOfDay
import com.example.campuslife.data.checkin.realCheckInDays
import com.example.campuslife.data.checkin.restCardDays
import com.example.campuslife.data.checkin.restCardStreakProgress
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.formatPlanDateLabel
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 统计页可选的时间窗口。
 *
 * 区间按日历天铺开：某天没有打卡也保留一个空行，避免把跳过的日期抹掉、看起来像连续打卡。
 */
enum class StatsRange(val days: Int, val label: String) {
    Week(7, "近 7 天"),
    Fortnight(14, "近 14 天"),
    Month(30, "近 30 天"),
}

val DefaultStatsRange = StatsRange.Week

/** 一组统计指标：最少 / 最多（或最早 / 最晚）+ 平均。 */
data class StatSummaryUiModel(
    val label: String,
    val minCaption: String,
    val minValue: String,
    val maxCaption: String,
    val maxValue: String,
    val averageLabel: String,
    val averageValue: String,
)

data class StatsTaskUiModel(
    val title: String,
    val supportingText: String,
    val isCompleted: Boolean,
)

/** 统计窗口里的一天。打卡与计划使用同一套凌晨 6 点换日规则。 */
data class StatsDayUiModel(
    val epochDay: Long,
    val axisLabel: String,
    val dateLabel: String,
    val isToday: Boolean,
    /** 当天 0 点起的分钟数，null 表示这天没有对应记录。 */
    val startMinutes: Float? = null,
    val endMinutes: Float? = null,
    val durationMinutes: Int? = null,
    /**
     * 当天已经开始、但还没结束打卡时的「此刻」。
     *
     * 甘特图的空心条要一直画到当前时刻，否则一根画满整行的条子看起来
     * 和已经结束的打卡没有区别。历史记录固定为 null。
     */
    val ongoingEndMinutes: Float? = null,
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val tasks: List<StatsTaskUiModel> = emptyList(),
    /** 这一天是用休息卡补记的。 */
    val isRestCardDay: Boolean = false,
    /** 这一天能不能用休息卡补（没有真实打卡、不是今天、不是未来）。 */
    val canUseRestCard: Boolean = false,
) {
    val hasCheckIn: Boolean get() = startMinutes != null
    val startLabel: String? get() = startMinutes?.roundToInt()?.let(::formatMinuteOfDay)
    val endLabel: String? get() = endMinutes?.roundToInt()?.let(::formatMinuteOfDay)
    val durationLabel: String? get() = durationMinutes?.let(::formatDurationLabel)
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
    val range: StatsRange = DefaultStatsRange,
    val rangeLabel: String,
    /** 窗口内的每一天，按时间正序，最后一个是今天。 */
    val days: List<StatsDayUiModel>,
    val startSummary: StatSummaryUiModel,
    val endSummary: StatSummaryUiModel,
    val durationSummary: StatSummaryUiModel,
    /** 历史全部已打卡天数（含换日自动补记的天），与窗口无关。 */
    val completedDays: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    /** 历史全部打卡时长合计，与窗口无关。 */
    val totalDurationLabel: String,
    /** 窗口内的打卡时长合计。 */
    val windowDurationLabel: String,
    /** 休息卡库存与进度。 */
    val restCards: RestCardUiState = RestCardUiState(),
    /** 每一弹各自的收集进度。 */
    val collections: List<CardCollectionUiState> = emptyList(),
    /** 已经发出来的休息卡，按获得时间倒序（最近的在最前）。 */
    val restCardGrants: List<RestCardGrantUiModel> = emptyList(),
    /** 被点开的那一天，null 表示详情弹层关着。 */
    val selectedDay: StatsDayUiModel? = null,
) {
    val dayLabels: List<String> get() = days.map { it.axisLabel }
    val startMinutes: List<Float?> get() = days.map { it.startMinutes }
    val endMinutes: List<Float?> get() = days.map { it.endMinutes }
    val durationMinutes: List<Int?> get() = days.map { it.durationMinutes }

    /** 第一弹兼容入口；新界面使用 [collections] 展示全部已上线卡池。 */
    val collection: CardCollectionUiState
        get() = collections.firstOrNull { it.series == CardSeries.Isekai }
            ?: CardCollectionUiState()

    /** 窗口内是否有任何打卡起点；没有就画不出甘特条，只能给提示。 */
    val hasChartData: Boolean get() = days.any { it.hasCheckIn }
}

val EmptyStatsUiState = StatsUiState(
    rangeLabel = "暂无记录",
    days = emptyList(),
    startSummary = StatSummaryUiModel("开始打卡时间", "最早", "--:--", "最晚", "--:--", "平均", "--:--"),
    endSummary = StatSummaryUiModel("结束打卡时间", "最早", "--:--", "最晚", "--:--", "平均", "--:--"),
    durationSummary = StatSummaryUiModel("每日打卡时长", "最少", "--", "最多", "--", "平均", "--"),
    completedDays = 0,
    currentStreakDays = 0,
    longestStreakDays = 0,
    totalDurationLabel = "--",
    windowDurationLabel = "--",
)

/**
 * 把真实打卡记录与对应日期的计划快照整理成统计页需要的形状。
 *
 * 已打卡口径见 [completedCheckInDays]：点了结束打卡，或该日已过且当天任务全勾
 * （完成全部任务但忘记点结束的，由换日自动补记）。图表只画真实记录；
 * 没有任何记录但存档里有补记天数时，成就卡照常显示，图区提示暂无数据。
 *
 * 未结束的当天也进窗口：它会出现在甘特图上（从开始打卡画到此刻），
 * 但结束时间与时长保持为空，不参与结束时间、时长统计。
 *
 * [nowMinuteOfDay] 只用来定位甘特图上「此刻」的位置，不参与任何汇总数字。
 */
fun buildStatsUiState(
    records: List<CheckInRecord>,
    todayEpochDay: Long,
    range: StatsRange = DefaultStatsRange,
    plans: List<DailyPlan> = emptyList(),
    selectedEpochDay: Long? = null,
    nowMinuteOfDay: Int? = null,
    /** 卡册（抽卡收集来的），只影响收集分区。 */
    ownedCards: CardCollection = CardCollection.Empty,
    /** 整套兑换换来的休息卡张数，要算进库存。 */
    bonusRestCards: Int = 0,
): StatsUiState {
    val completedEpochDays = completedCheckInDays(records, plans, todayEpochDay)
    if (records.isEmpty() && completedEpochDays.isEmpty()) {
        return EmptyStatsUiState.copy(
            range = range,
            rangeLabel = "暂无记录",
            // 记录全无时成就值仍为 0，但卡册是另一份数据，照常显示。
            collections = buildCollectionUiStates(ownedCards),
        )
    }

    // 奖励口径只认真实打卡（休息卡补出来的那天不算），口径见 realCheckInDays。
    val realEpochDays = realCheckInDays(records, plans, todayEpochDay)
    val restEpochDays = restCardDays(records)
    val grants = earnedRestCards(realEpochDays)
    val usedRestDays = restEpochDays.sorted()

    val recordByDay = records.associateBy { it.epochDay }
    val planByDay = plans.associateBy { it.epochDay }
    val days = (todayEpochDay - (range.days - 1)..todayEpochDay).map { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        val record = recordByDay[epochDay]
        val plan = planByDay[epochDay]
        StatsDayUiModel(
            epochDay = epochDay,
            axisLabel = "${date.monthValue}/${date.dayOfMonth}",
            dateLabel = formatPlanDateLabel(date),
            isToday = epochDay == todayEpochDay,
            startMinutes = record?.startMinuteOfDay?.toFloat(),
            endMinutes = record?.endMinuteOfDay?.toFloat(),
            durationMinutes = record?.durationMinutes,
            // 只有「已经开始、还没结束」的当天才需要这个时刻，免得空心条画满整行。
            ongoingEndMinutes = record
                ?.takeIf { it.endMinuteOfDay == null && epochDay == todayEpochDay }
                ?.let { nowMinuteOfDay?.toFloat() },
            plannedCount = plan?.totalCount ?: 0,
            completedCount = plan?.completedCount ?: 0,
            tasks = plan?.orderedTasks?.map { task ->
                StatsTaskUiModel(
                    title = task.title,
                    supportingText = task.supportingText,
                    isCompleted = task.isCompleted,
                )
            }.orEmpty(),
            isRestCardDay = epochDay in restEpochDays,
            // 只补过去的日子：今天该由用户自己走完，未来没发生也无从补起。
            canUseRestCard = epochDay < todayEpochDay &&
                epochDay !in completedEpochDays &&
                epochDay !in restEpochDays,
        )
    }

    val windowStarts = days.mapNotNull { it.startMinutes?.roundToInt() }
    val windowEnds = days.mapNotNull { it.endMinutes?.roundToInt() }
    val windowDurations = days.mapNotNull { it.durationMinutes }
    val completedDayList = completedEpochDays.toList()

    return StatsUiState(
        range = range,
        rangeLabel = range.label,
        days = days,
        startSummary = timeSummary("开始打卡时间", windowStarts),
        endSummary = timeSummary("结束打卡时间", windowEnds),
        durationSummary = durationSummary(windowDurations),
        completedDays = completedDayList.size,
        currentStreakDays = currentStreak(completedDayList, todayEpochDay),
        longestStreakDays = longestStreak(completedDayList),
        totalDurationLabel = formatDurationLabel(
            records.mapNotNull { it.durationMinutes }.sum(),
        ),
        windowDurationLabel = formatDurationLabel(windowDurations.sum()),
        restCards = RestCardUiState(
            // 库存 = 打卡攒出来的 + 整套兑换换来的。
            earned = grants.size + bonusRestCards,
            used = usedRestDays.size,
            // 一轮没攒满时，进度就是当前这段真实连续；攒满过后面又断了，也从头算。
            progressDays = restCardStreakProgress(realEpochDays, todayEpochDay),
            latestEarnedEpochDay = grants.lastOrNull()?.earnedEpochDay,
            fromExchange = bonusRestCards,
        ),
        // 库存按「最早的卡先核销」记账：界面上把已用天数依次配给最早的几张卡。
        restCardGrants = grants.mapIndexed { index, grant ->
            val usedDay = usedRestDays.getOrNull(index)
            RestCardGrantUiModel(
                earnedEpochDay = grant.earnedEpochDay,
                earnedDateLabel = formatEpochDayLabel(grant.earnedEpochDay),
                isUsed = usedDay != null,
                usedEpochDay = usedDay,
                usedDateLabel = usedDay?.let(::formatEpochDayLabel),
            )
        }.reversed(),
        collections = buildCollectionUiStates(ownedCards),
        selectedDay = days.firstOrNull { it.epochDay == selectedEpochDay },
    )
}

/**
 * 卡册 → 统计页的收集进度。
 *
 * 固定按卡表顺序铺满每一格（没拿到的也占一格），这样「还差几种」一眼能算出来；
 * 每格的上限是 [MAX_COPIES_PER_CARD]，拿到 2 张的那格就满了。
 */
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

private fun timeSummary(label: String, minutes: List<Int>): StatSummaryUiModel {
    if (minutes.isEmpty()) {
        return StatSummaryUiModel(label, "最早", "--:--", "最晚", "--:--", "平均", "--:--")
    }
    return StatSummaryUiModel(
        label = label,
        minCaption = "最早",
        minValue = formatMinuteOfDay(minutes.minOrNull() ?: 0),
        maxCaption = "最晚",
        maxValue = formatMinuteOfDay(minutes.maxOrNull() ?: 0),
        averageLabel = "平均",
        averageValue = formatMinuteOfDay(minutes.average().roundToInt()),
    )
}

private fun durationSummary(minutes: List<Int>): StatSummaryUiModel {
    if (minutes.isEmpty()) {
        return StatSummaryUiModel("每日打卡时长", "最少", "--", "最多", "--", "平均", "--")
    }
    return StatSummaryUiModel(
        label = "每日打卡时长",
        minCaption = "最少",
        minValue = formatDurationLabel(minutes.minOrNull() ?: 0),
        maxCaption = "最多",
        maxValue = formatDurationLabel(minutes.maxOrNull() ?: 0),
        averageLabel = "平均",
        averageValue = formatDurationLabel(minutes.average().roundToInt()),
    )
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

/** 甘特图时间轴：一天之内的分钟数范围。 */
internal data class GanttAxis(
    val startMinute: Int,
    val endMinute: Int,
) {
    val spanMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(1)

    /** 时刻 → 0f..1f 的横向位置；超出轴范围的会被夹回边界。 */
    fun fractionOf(minute: Float): Float =
        ((minute - startMinute) / spanMinutes.toFloat()).coerceIn(0f, 1f)
}

private const val GANTT_MIN_SPAN_MINUTES = 120
private const val MINUTES_PER_HOUR = 60
private const val MAX_MINUTE_OF_DAY = 24 * 60

/**
 * 甘特图横轴范围：按小时取整，并保证至少两小时的跨度。
 *
 * 只覆盖真正有记录的那几个钟点，不固定铺满 24 小时——
 * 否则打卡都挤在 08:00–20:00 时，条子会短得看不出差别。
 */
internal fun ganttAxisFor(days: List<StatsDayUiModel>): GanttAxis {
    val points = days.flatMap { day ->
        listOfNotNull(day.startMinutes, day.endMinutes, day.ongoingEndMinutes)
    }
    if (points.isEmpty()) {
        return GanttAxis(startMinute = 0, endMinute = GANTT_MIN_SPAN_MINUTES)
    }
    var start = (points.min() / MINUTES_PER_HOUR).toInt() * MINUTES_PER_HOUR
    var end = (ceil(points.max() / MINUTES_PER_HOUR).toInt() * MINUTES_PER_HOUR)
        .coerceAtMost(MAX_MINUTE_OF_DAY)
    // 先按末端撑开跨度，再让起点跟随：反过来做的话，末端会被夹到记录之前，
    // 那天的甘特条就画到轴外面去了。右端已经靠到 24:00 时，只能让起点往左退。
    if (end - start < GANTT_MIN_SPAN_MINUTES) {
        end = (start + GANTT_MIN_SPAN_MINUTES).coerceAtMost(MAX_MINUTE_OF_DAY)
        start = (end - GANTT_MIN_SPAN_MINUTES).coerceAtLeast(0)
    }
    return GanttAxis(startMinute = start, endMinute = end)
}

/**
 * 时间轴上的刻度时刻：起点、按整点步长推进的中间刻度与轴末端。
 *
 * 近 7 天用 2 小时间隔、近 30 天放宽到 4 小时，避免刻度挤成一片。
 * 两端都要挂刻度——轴范围按整点取整，起点与末端都是整点，
 * 首尾标签各自对齐图表两侧，才不会和真实范围对不上。
 */
internal fun ganttTimeTicks(axis: GanttAxis, dayCount: Int): List<Int> {
    val stepHours = if (dayCount <= 14) 2 else 4
    val stepMinutes = stepHours * MINUTES_PER_HOUR
    return buildList {
        var minute = axis.startMinute
        while (minute < axis.endMinute) {
            add(minute)
            minute += stepMinutes
        }
        add(axis.endMinute)
    }.distinct()
}
