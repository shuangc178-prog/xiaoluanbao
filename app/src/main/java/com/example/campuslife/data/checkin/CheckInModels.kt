package com.example.campuslife.data.checkin

import com.example.campuslife.data.plan.DailyPlan
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

/** 打卡日以凌晨 6 点为界：00:00–05:59 仍算作前一天。 */
const val CHECK_IN_DAY_START_HOUR = 6

private const val MINUTES_PER_DAY = 24 * 60

/**
 * 一条打卡记录的来源。
 *
 * [Real] 是用户自己点出来的真实打卡；[RestCard] 是用休息卡补记的一天——
 * 它把那天「改成已打卡」，起止时刻按历史平均值写入，用来补齐断掉的那一格。
 * 两者在图表上一样画、在累计天数里一样算，但**奖励口径只认真实打卡**：
 * 用卡补出来的天数不计入「连续打卡满 5 天」的进度，否则用卡就能滚出更多卡。
 */
enum class CheckInRecordKind { Real, RestCard }

/**
 * 一次打卡的完整周期。
 *
 * 时间统一用「当天 0 点起的分钟数」保存，好处是画折线图、算平均值、跨日比较都只是整数运算，
 * 不用带着时区到处跑。
 */
data class CheckInRecord(
    val epochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int? = null,
    val kind: CheckInRecordKind = CheckInRecordKind.Real,
) {
    val isCompleted: Boolean get() = endMinuteOfDay != null

    /** 用休息卡补记的一天。 */
    val isRestCardDay: Boolean get() = kind == CheckInRecordKind.RestCard

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    /** 打卡时长 = 结束打卡 − 开始打卡。未结束时为空，不参与统计。 */
    val durationMinutes: Int?
        get() = endMinuteOfDay?.let { end ->
            val crossesMidnight = end < startMinuteOfDay
            (end - startMinuteOfDay + if (crossesMidnight) MINUTES_PER_DAY else 0)
                .coerceAtLeast(0)
        }
}

/** 首页打卡按钮的三个状态。 */
enum class CheckInPhase { Idle, Started, Finished }

/**
 * 一个打卡日是否记为「已打卡」：
 * - 用户点了结束打卡（记录带结束时间）；或
 * - 该日已过、当天安排了任务且全部完成——完成全部任务后忘记点结束按钮的，
 *   到 6 点换日时自动补记为已打卡。
 *
 * 因此「未打卡」只有两种情况：当天没结束打卡且任务没勾完，或当天没有安排任务。
 * 当天任务全勾但还没点结束时不提前计：等结束打卡或换日才落定。
 *
 * 图表（开始/结束时间、打卡时长）仍然只画真实记录；自动补记的天只进
 * 累计天数与连续天数，不伪造开始/结束时刻。
 */
fun completedCheckInDays(
    records: List<CheckInRecord>,
    plans: List<DailyPlan>,
    todayEpochDay: Long,
): Set<Long> {
    val days = records.asSequence()
        .filter { it.isCompleted }
        .map { it.epochDay }
        .toMutableSet()
    plans.forEach { plan ->
        if (plan.epochDay < todayEpochDay && plan.isAllCompleted) days += plan.epochDay
    }
    return days
}

/** 把时间点换算成「当天 0 点起的分钟数」。 */
fun ZonedDateTime.toMinuteOfDay(): Int = hour * 60 + minute

/**
 * 真实打卡的天：点了结束打卡、且不是用休息卡补记的。
 *
 * 换日自动补记（任务全勾但没点结束）算真实打卡——那是人真的做完了任务；
 * 休息卡补出来的不算，它是奖励兑换的产物，不能反过来喂养奖励。
 */
fun realCheckInDays(
    records: List<CheckInRecord>,
    plans: List<DailyPlan>,
    todayEpochDay: Long,
): Set<Long> = completedCheckInDays(records, plans, todayEpochDay)
    .filterNot { day -> records.any { it.epochDay == day && it.isRestCardDay } }
    .toSet()

/** 用休息卡补记过的那些天。 */
fun restCardDays(records: List<CheckInRecord>): Set<Long> =
    records.filter { it.isRestCardDay }.map { it.epochDay }.toSet()

/**
 * 一张已经发到手的休息卡。
 *
 * [earnedEpochDay] 是攒满 [REST_CARD_STREAK_DAYS] 天的那一天（也就是第 5、10、15…天）。
 */
data class RestCardGrant(
    val earnedEpochDay: Long,
    val streakDays: Int,
)

/** 每连续真实打卡这么多天，发一张休息卡。 */
const val REST_CARD_STREAK_DAYS = 5

/**
 * 已经发出去的休息卡，按获得时间正序。
 *
 * 口径：只看**真实打卡**的连续段，每满 [REST_CARD_STREAK_DAYS] 天发一张；
 * 用休息卡补记的天不算——否则补一天就能再滚出一张卡。
 * 发卡是纯推导（不落库），所以历史怎么变，卡的数量与获得日期都能自洽地重算，
 * 不会出现「库存说有卡、记录里找不到来源」的漂移。
 */
fun earnedRestCards(
    realDays: Set<Long>,
): List<RestCardGrant> {
    if (realDays.isEmpty()) return emptyList()
    val sorted = realDays.distinct().sorted()
    val grants = mutableListOf<RestCardGrant>()
    var run = 1
    for (index in 1..sorted.lastIndex) {
        run = if (sorted[index] == sorted[index - 1] + 1) run + 1 else 1
        if (run % REST_CARD_STREAK_DAYS == 0) {
            grants += RestCardGrant(earnedEpochDay = sorted[index], streakDays = run)
        }
    }
    return grants
}

/**
 * 当前这一轮真实连续打卡已经攒了几天（用于「再坚持 N 天得一张」的进度）。
 *
 * 今天还没打卡不算断：从今天往回数时，今天没有就从昨天起算。
 */
fun restCardStreakProgress(
    realDays: Set<Long>,
    todayEpochDay: Long,
): Int {
    if (realDays.isEmpty()) return 0
    var cursor = if (todayEpochDay in realDays) todayEpochDay else todayEpochDay - 1
    var streak = 0
    while (cursor in realDays) {
        streak += 1
        cursor -= 1
    }
    return streak
}

/** 用卡补记时写入的平均时刻：开始、结束、时长。没有历史记录时给一组默认值。 */
data class AverageCheckInTimes(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    val durationMinutes: Int get() = (endMinuteOfDay - startMinuteOfDay).coerceAtLeast(0)
}

/** 没有可参考的历史记录时的兜底时刻：上午九点开始、下午五点结束。 */
val DefaultAverageCheckInTimes = AverageCheckInTimes(
    startMinuteOfDay = 9 * 60,
    endMinuteOfDay = 17 * 60,
)

/**
 * 按已有真实记录的平均值算出一组时刻，供休息卡补记使用。
 *
 * 只统计真实打卡，不把之前用卡补出来的时刻再平均进去——否则用卡越多，
 * 补出来的时刻越向自己收敛，慢慢偏离用户真实的作息。
 */
fun averageCheckInTimes(records: List<CheckInRecord>): AverageCheckInTimes {
    val real = records.filter { it.isCompleted && !it.isRestCardDay }
    val starts = real.map { it.startMinuteOfDay }
    val ends = real.mapNotNull { it.endMinuteOfDay }
    if (starts.isEmpty() || ends.isEmpty()) return DefaultAverageCheckInTimes
    return AverageCheckInTimes(
        startMinuteOfDay = starts.average().roundToInt(),
        endMinuteOfDay = ends.average().roundToInt(),
    )
}

/** 当前应该归属的打卡日（凌晨 6 点换日）。 */
fun ZonedDateTime.checkInEpochDay(): Long =
    minusHours(CHECK_IN_DAY_START_HOUR.toLong()).toLocalDate().toEpochDay()

/** 距离下一次换日（次日凌晨 6 点）还有多少毫秒。 */
fun ZonedDateTime.millisUntilNextCheckInDay(): Long {
    var next = toLocalDate().atTime(CHECK_IN_DAY_START_HOUR, 0).atZone(zone)
    if (!next.isAfter(this)) next = next.plusDays(1)
    return java.time.Duration.between(this, next).toMillis()
}

/** `512` → `08:32`。 */
fun formatMinuteOfDay(minuteOfDay: Int): String {
    val normalized = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return String.format(Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60)
}

/** `205` → `3 小时 25 分`；不足一小时只显示分钟。 */
fun formatDurationLabel(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val remainder = safe % 60
    return when {
        hours == 0 -> "$remainder 分"
        remainder == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainder 分"
    }
}
