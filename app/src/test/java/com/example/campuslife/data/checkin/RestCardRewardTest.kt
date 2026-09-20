package com.example.campuslife.data.checkin

import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 休息卡的奖励口径。
 *
 * 产品的三条硬规则：
 * ① 连续**真实**打卡每满 5 天发一张休息卡；
 * ② 用休息卡补出来的天数**不算**奖励机制里的连续打卡；
 * ③ 补记的起止时刻取历史平均值，且不给真实记录改写。
 */
class RestCardRewardTest {

    private val today: Long = LocalDate.of(2026, 9, 18).toEpochDay()

    /** 这一天的 epochDay：写在注释里方便核对上面那些 `today - n` 的期望值。 */
    private val todayEpochDay = 20714L

    private fun real(day: Long, start: Int = 9 * 60, end: Int = 17 * 60) = CheckInRecord(
        epochDay = day,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
    )

    private fun rest(day: Long, start: Int = 9 * 60, end: Int = 17 * 60) = CheckInRecord(
        epochDay = day,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        kind = CheckInRecordKind.RestCard,
    )

    private fun planOf(day: Long, allDone: Boolean) = DailyPlan(
        epochDay = day,
        tasks = listOf(
            PlanTask(title = "写作业", supportingText = "晚上 8 点前", isCompleted = allDone),
        ),
    )

    // ===== 发卡 =====

    @Test
    fun fourDaysEarnNothing() {
        val records = (0 until 4).map { real(today - it) }
        assertEquals(0, earnedRestCards(realCheckInDays(records, emptyList(), today)).size)
    }

    @Test
    fun fiveConsecutiveDaysEarnOneCard() {
        val records = (0 until 5).map { real(today - it) }
        val grants = earnedRestCards(realCheckInDays(records, emptyList(), today))
        assertEquals(1, grants.size)
        // 发卡日 = 攒满的那天，也就是这段连续里最晚的一天（今天）。
        assertEquals(today, grants.single().earnedEpochDay)
        assertEquals(5, grants.single().streakDays)
    }

    @Test
    fun tenConsecutiveDaysEarnTwoCards() {
        val records = (0 until 10).map { real(today - it) }
        val grants = earnedRestCards(realCheckInDays(records, emptyList(), today))
        // 第 5 天与第 10 天各一张，从最早那天往今天数。
        assertEquals(listOf(today - 5, today), grants.map { it.earnedEpochDay })
        assertEquals(listOf(5, 10), grants.map { it.streakDays })
    }

    @Test
    fun aGapResetsTheRun() {
        // 4 天 + 空一天 + 5 天：只有后面那 5 天算数，发卡日在那一段的末尾。
        val records = (0 until 4).map { real(today - it) } + (5 until 10).map { real(today - it) }
        val grants = earnedRestCards(realCheckInDays(records, emptyList(), today))
        assertEquals(1, grants.size)
        assertEquals(today - 5, grants.single().earnedEpochDay)
    }

    @Test
    fun restCardDaysDoNotFeedTheRewardStreak() {
        // 关键规则②：4 天真实 + 用卡补的那天 + 再 1 天真实 ≠ 连续 6 天。
        // 补记的那天既不算真实打卡，也不进入发卡进度。
        val records = listOf(real(today - 5), real(today - 4), real(today - 3), real(today - 2)) +
            rest(today - 1) +
            real(today)
        val realDays = realCheckInDays(records, emptyList(), today)
        assertEquals(setOf(today - 5, today - 4, today - 3, today - 2, today), realDays)
        // 真实连续只有 1（今天），不是 6。
        assertEquals(1, restCardStreakProgress(realDays, today))
        assertEquals(0, earnedRestCards(realDays).size)
    }

    @Test
    fun autoFilledDaysCountAsRealCheckIns() {
        // 换日自动补记（任务全勾但没点结束）是人真的做完了任务，算真实打卡。
        // 今天还没换日，所以今天那张快照不算——只有它之前的 4 天进账。
        val plans = (0 until 5).map { planOf(today - it, allDone = true) }
        val realDays = realCheckInDays(emptyList(), plans, today)
        assertEquals(4, realDays.size)

        // 再往前补一天，凑满 5 天就该发卡：发卡日是这 5 天里最晚的那天（昨天）。
        val sixPlans = (0 until 6).map { planOf(today - it, allDone = true) }
        val sixDays = realCheckInDays(emptyList(), sixPlans, today)
        assertEquals(5, sixDays.size)
        assertEquals(today - 1, earnedRestCards(sixDays).single().earnedEpochDay)
    }

    @Test
    fun todayWithoutTasksDoesNotEarnByItself() {
        // 空任务日永远不补记，也就永远不产生卡。
        assertTrue(realCheckInDays(emptyList(), listOf(planOf(today, allDone = false)), today).isEmpty())
    }

    // ===== 进度 =====

    @Test
    fun progressCountsBackFromToday() {
        val realDays = (0 until 3).map { today - it }.toSet()
        assertEquals(3, restCardStreakProgress(realDays, today))
    }

    @Test
    fun todayWithoutCheckInContinuesFromYesterday() {
        val realDays = (1 until 4).map { today - it }.toSet()
        assertEquals(3, restCardStreakProgress(realDays, today))
    }

    @Test
    fun progressStopsAtTheFirstGap() {
        val realDays = setOf(today, today - 1, today - 3, today - 4)
        assertEquals(2, restCardStreakProgress(realDays, today))
    }

    // ===== 平均时刻 =====

    @Test
    fun averageUsesRealRecordsOnly() {
        val records = listOf(
            real(today - 2, start = 8 * 60, end = 16 * 60),
            real(today - 1, start = 10 * 60, end = 18 * 60),
            // 之前用卡补出来的时刻不再参与平均，否则会越补越偏。
            rest(today, start = 0, end = 23 * 60),
        )
        val times = averageCheckInTimes(records)
        assertEquals(9 * 60, times.startMinuteOfDay)
        assertEquals(17 * 60, times.endMinuteOfDay)
        assertEquals(8 * 60, times.durationMinutes)
    }

    @Test
    fun averageFallsBackToDefaultsWithoutHistory() {
        assertEquals(DefaultAverageCheckInTimes, averageCheckInTimes(emptyList()))
        assertEquals(DefaultAverageCheckInTimes, averageCheckInTimes(listOf(rest(today))))
    }

    // ===== 用卡补记的结果形状 =====

    @Test
    fun restCardRecordIsCompletedAndMarked() {
        val record = rest(today - 1, start = 9 * 60, end = 17 * 60)
        assertTrue(record.isCompleted)
        assertTrue(record.isRestCardDay)
        assertEquals(8 * 60, record.durationMinutes)
        // 补记的天照样进「已打卡」累计，才谈得上补上断掉的那一格。
        val completed = completedCheckInDays(listOf(record), emptyList(), today)
        assertTrue(today - 1 in completed)
        // 但不进奖励口径。
        assertFalse((today - 1) in realCheckInDays(listOf(record), emptyList(), today))
    }

    @Test
    fun usedRestDaysAreListed() {
        val records = listOf(rest(today - 3), rest(today - 1), real(today - 2))
        assertEquals(setOf(today - 3, today - 1), restCardDays(records))
    }
}
