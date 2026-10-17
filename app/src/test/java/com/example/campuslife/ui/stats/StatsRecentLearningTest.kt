package com.example.campuslife.ui.stats

import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.data.plan.learningDayEndMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsRecentLearningTest {
    private val today = LocalDate.of(2026, 1, 3).toEpochDay()
    private val noon = LocalDate.ofEpochDay(today).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun windowKeepsSevenChronologicalDatesAcrossYearAndFillsMissingPlansWithZero() {
        val plans = listOf(
            timedPlan(today, "今天", 120_000L),
            timedPlan(today - 6, "窗口首日", 60_000L),
            timedPlan(today - 7, "窗口之前", 900_000L),
            timedPlan(today + 1, "未来", 900_000L),
        )
        val state = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon)

        assertEquals((today - 6..today).toList(), state.recentDays.map { it.epochDay })
        assertEquals(listOf(60_000L, 0L, 0L, 0L, 0L, 0L, 120_000L),
            state.recentDays.map { it.learningDurationMillis })
        assertEquals(listOf(today), state.recentDays.filter { it.isToday }.map { it.epochDay })
        assertEquals(180_000L, state.recentDays.sumOf { it.learningDurationMillis })
    }

    @Test
    fun selectingEveryBarReturnsItsExactTaskBreakdownIncludingZeroDays() {
        val plans = listOf(DailyPlan(today - 2, listOf(
            PlanTask("较晚任务", "", expectedStartMinute = 600, learningElapsedMillis = 30_000L),
            PlanTask("较早任务", "", expectedStartMinute = 480, learningElapsedMillis = 90_000L),
        )))
        val initial = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon)

        initial.recentDays.forEach { bar ->
            val selected = buildStatsUiState(emptyList(), today, plans,
                selectedEpochDay = bar.epochDay, nowEpochMillis = noon)
            assertEquals(bar, selected.day)
            assertEquals(initial.recentDays, selected.recentDays)
            assertEquals(bar.learningDurationMillis, selected.day.tasks.sumOf { it.learningDurationMillis })
        }
        assertEquals(listOf("较早任务", "较晚任务"), initial.recentDays[4].tasks.map { it.title })
        assertEquals(120_000L, initial.recentDays[4].learningDurationMillis)
        assertTrue(initial.recentDays[1].tasks.isEmpty())
    }

    @Test
    fun oldHistorySelectionDoesNotMoveTheRecentWindowOrChangeLifetimeTotal() {
        val plans = listOf(timedPlan(today, "今天", 60_000L), timedPlan(today - 50, "历史", 120_000L))
        val current = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon)
        val history = buildStatsUiState(emptyList(), today, plans,
            selectedEpochDay = today - 50, nowEpochMillis = noon)

        assertEquals(current.recentDays, history.recentDays)
        assertEquals("历史", history.day.tasks.single().title)
        assertEquals("03:00", history.totalDurationLabel)
        assertEquals(current.totalDurationLabel, history.totalDurationLabel)
    }

    @Test
    fun checkInAndRestCardNeverInventWeeklyLearningTime() {
        val state = buildStatsUiState(listOf(
            CheckInRecord(today, 480, 1200),
            CheckInRecord(today - 1, 480, 1200, CheckInRecordKind.RestCard),
        ), today, plans = listOf(DailyPlan(today, listOf(PlanTask("未计时", "", isCompleted = true)))),
            nowEpochMillis = noon)

        assertTrue(state.recentDays.all { it.learningDurationMillis == 0L })
        assertTrue(state.recentDays[5].isRestCardDay)
        assertEquals(1, state.recentDays.last().tasks.size)
        assertFalse(state.recentDays[5].canUseRestCard)
    }

    @Test
    fun liveTimeUpdatesWeeklyBarsAndExpiredHistoricalTimersRemainCapped() {
        val boundary = learningDayEndMillis(today - 1)
        val plans = listOf(
            DailyPlan(today, listOf(PlanTask("进行中", "", learningElapsedMillis = 60_000L,
                learningStartedAtEpochMillis = noon - 120_000L))),
            DailyPlan(today - 1, listOf(PlanTask("跨日暂停", "",
                learningStartedAtEpochMillis = boundary - 60_000L))),
        )
        val first = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon)
        val later = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon + 30_000L)

        assertEquals(180_000L, first.recentDays.last().learningDurationMillis)
        assertEquals(210_000L, later.recentDays.last().learningDurationMillis)
        assertEquals(later.day.learningDurationMillis, later.recentDays.last().learningDurationMillis)
        assertEquals(60_000L, later.recentDays[5].learningDurationMillis)
        assertEquals(TaskLearningPhase.Paused, later.recentDays[5].tasks.single().learningPhase)
        assertEquals(first.recentDays[5], later.recentDays[5])
    }

    @Test
    fun nextLogicalDaySlidesWindowAndKeepsThePreviousDaysTotals() {
        val plans = listOf(timedPlan(today, "今天", 60_000L), timedPlan(today - 6, "将移出窗口", 120_000L))
        val current = buildStatsUiState(emptyList(), today, plans, nowEpochMillis = noon)
        val next = buildStatsUiState(emptyList(), today + 1, plans,
            nowEpochMillis = learningDayEndMillis(today))

        assertEquals(current.recentDays.drop(1).map { it.learningDurationMillis },
            next.recentDays.dropLast(1).map { it.learningDurationMillis })
        assertEquals((today - 5..today + 1).toList(), next.recentDays.map { it.epochDay })
        assertEquals(0L, next.recentDays.last().learningDurationMillis)
        assertTrue(next.recentDays.last().isToday)
        assertFalse(next.recentDays[5].isToday)
    }

    private fun timedPlan(epochDay: Long, title: String, millis: Long) =
        DailyPlan(epochDay, listOf(PlanTask(title, "", learningElapsedMillis = millis)))
}
