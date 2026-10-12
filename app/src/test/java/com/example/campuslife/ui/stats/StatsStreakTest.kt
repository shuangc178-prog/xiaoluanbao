package com.example.campuslife.ui.stats

import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 统计页的真实数据口径。
 *
 * 这些测试刻意不使用预置数据：统计页上线后只能展示用户自己的打卡记录，
 * 同时保留窗口中的空白天，避免把缺失日期伪装成连续打卡。
 */
class StatsStreakTest {

    private val today: Long = LocalDate.of(2026, 9, 14).toEpochDay()
    private val windowStart = LocalDate.of(2026, 9, 8).toEpochDay()

    @Test
    fun noRecordsMeansNoStreak() {
        assertEquals(0, currentStreak(emptyList(), today))
        assertEquals(0, longestStreak(emptyList()))
    }

    @Test
    fun streakCountsBackFromToday() {
        assertEquals(3, currentStreak(listOf(today - 2, today - 1, today), today))
    }

    @Test
    fun todayWithoutACompletedRecordContinuesFromYesterday() {
        assertEquals(2, currentStreak(listOf(today - 2, today - 1), today))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        assertEquals(0, currentStreak(listOf(today - 4, today - 3), today))
        assertEquals(2, currentStreak(listOf(today - 5, today - 1, today), today))
    }

    @Test
    fun longestStreakFindsTheLongestRunAndIgnoresDuplicates() {
        assertEquals(3, longestStreak(listOf(1, 2, 3, 7, 8)))
        assertEquals(3, longestStreak(listOf(3, 1, 2, 2, 1)))
        assertEquals(1, longestStreak(listOf(5)))
    }

    @Test
    fun dailyLearningUsesTasksAndNeverCheckInDuration() {
        val state = buildStatsUiState(
            records = listOf(CheckInRecord(today, 8 * 60, 20 * 60)),
            todayEpochDay = today,
            plans = listOf(DailyPlan(today, listOf(
                PlanTask("数学", "", learningElapsedMillis = 60_000L),
                PlanTask("英语", "", learningElapsedMillis = 120_000L),
            ))),
        )
        assertEquals("03:00", state.day.durationLabel)
        assertEquals("03:00", state.totalDurationLabel)
        assertEquals(2, state.day.tasks.size)
        assertEquals("08:00", state.day.startLabel)
    }

    @Test
    fun historyCanShowAnyDateAndChangesOnlyDailyData() {
        val plans = listOf(
            DailyPlan(today, listOf(PlanTask("今天", "", learningElapsedMillis = 60_000L))),
            DailyPlan(today - 100, listOf(PlanTask("历史", "", learningElapsedMillis = 120_000L))),
        )
        val current = buildStatsUiState(emptyList(), today, plans = plans)
        val historical = buildStatsUiState(emptyList(), today, plans = plans, selectedEpochDay = today - 100)
        assertEquals("01:00", current.day.durationLabel)
        assertEquals("02:00", historical.day.durationLabel)
        assertEquals("历史", historical.day.tasks.single().title)
        assertEquals(current.totalDurationLabel, historical.totalDurationLabel)
        assertEquals(current.restCards, historical.restCards)
        assertFalse(historical.day.isToday)
    }

    @Test
    fun emptyOrUntimedDayNeverFallsBackToFakeOrCheckInTime() {
        val empty = buildStatsUiState(emptyList(), today, selectedEpochDay = today - 50)
        assertTrue(empty.day.tasks.isEmpty())
        assertEquals("00:00", empty.day.durationLabel)
        val untimed = buildStatsUiState(listOf(CheckInRecord(today, 480, 1080)), today,
            plans = listOf(DailyPlan(today, listOf(PlanTask("旧任务", "", isCompleted = true)))))
        assertEquals("00:00", untimed.totalDurationLabel)
        assertTrue(untimed.day.isCheckedIn)
    }

    @Test
    fun ongoingTaskUpdatesBothDailyAndHistoricalTotals() {
        val now = java.time.LocalDate.ofEpochDay(today).atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val plans = listOf(DailyPlan(today, listOf(PlanTask("正在学习", "",
            learningElapsedMillis = 60_000L, learningStartedAtEpochMillis = now - 120_000L))))
        val first = buildStatsUiState(emptyList(), today, plans = plans, nowEpochMillis = now)
        val later = buildStatsUiState(emptyList(), today, plans = plans, nowEpochMillis = now + 30_000L)
        assertEquals("03:00", first.day.durationLabel)
        assertEquals("03:30", later.day.durationLabel)
        assertEquals(later.day.durationLabel, later.totalDurationLabel)
    }

    @Test
    fun restCardAndAutomaticallyCompletedDayDoNotCreateLearningTime() {
        val record = CheckInRecord(today - 1, 540, 1020,
            com.example.campuslife.data.checkin.CheckInRecordKind.RestCard)
        val plans = listOf(DailyPlan(today - 2, listOf(PlanTask("已完成", "", isCompleted = true))))
        val state = buildStatsUiState(listOf(record), today, plans = plans, selectedEpochDay = today - 1)
        assertEquals(2, state.completedDays)
        assertEquals("00:00", state.totalDurationLabel)
        assertEquals("00:00", state.day.durationLabel)
        assertTrue(state.day.isRestCardDay)
    }

    @Test
    fun futureDateFallsBackToTodayAndFuturePlansAreExcludedFromLearningTotal() {
        val future = DailyPlan(today + 1, listOf(PlanTask("未来", "", learningElapsedMillis = 600_000L)))
        val state = buildStatsUiState(emptyList(), today, plans = listOf(future), selectedEpochDay = today + 1)
        assertEquals(today, state.day.epochDay)
        assertEquals("00:00", state.totalDurationLabel)
    }

    @Test
    fun expiredHistoricalRunningTaskIsCappedAndShownPaused() {
        val boundary = com.example.campuslife.data.plan.learningDayEndMillis(today - 1)
        val plans = listOf(DailyPlan(today - 1, listOf(PlanTask("昨天", "",
            learningStartedAtEpochMillis = boundary - 60_000L))))
        val state = buildStatsUiState(emptyList(), today, plans = plans,
            selectedEpochDay = today - 1, nowEpochMillis = boundary + 7_200_000L)
        assertEquals("01:00", state.day.durationLabel)
        assertEquals(com.example.campuslife.data.plan.TaskLearningPhase.Paused, state.day.tasks.single().learningPhase)
    }
}
