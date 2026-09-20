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
    fun summaryAveragesAndExtremesComeFromCompletedRecords() {
        val records = listOf(
            CheckInRecord(windowStart, startMinuteOfDay = 420, endMinuteOfDay = 600),
            CheckInRecord(windowStart + 1, startMinuteOfDay = 480, endMinuteOfDay = 840),
            CheckInRecord(windowStart + 2, startMinuteOfDay = 450, endMinuteOfDay = 660),
        )

        val state = buildStatsUiState(
            records = records,
            todayEpochDay = windowStart + 2,
        )

        assertEquals(3, state.completedDays)
        assertEquals("最早", state.startSummary.minCaption)
        assertEquals("07:00", state.startSummary.minValue)
        assertEquals("08:00", state.startSummary.maxValue)
        assertEquals("07:30", state.startSummary.averageValue)
        assertEquals("3 小时", state.durationSummary.minValue)
        assertEquals("6 小时", state.durationSummary.maxValue)
        assertEquals("4 小时 10 分", state.durationSummary.averageValue)
        assertEquals(3, state.currentStreakDays)
        assertEquals(3, state.longestStreakDays)
        assertEquals(StatsRange.Week.days, state.days.size)
        assertEquals(listOf("9/8", "9/9", "9/10"), state.dayLabels.takeLast(3))
    }

    @Test
    fun windowKeepsEmptyDaysInsteadOfSkippingThem() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 420, endMinuteOfDay = 600),
                CheckInRecord(windowStart + 3, startMinuteOfDay = 450, endMinuteOfDay = 660),
            ),
            todayEpochDay = windowStart + 3,
        )

        assertEquals(StatsRange.Week.days, state.days.size)
        assertNull(state.days[1].startMinutes)
        assertNull(state.days[2].durationMinutes)
        assertEquals(2, state.startMinutes.filterNotNull().size)
    }

    @Test
    fun rangeSwitchesWindowLengthButKeepsHistoricalTotals() {
        val records = (0L until 30L).map { offset ->
            CheckInRecord(
                epochDay = today - offset,
                startMinuteOfDay = 420,
                endMinuteOfDay = 600,
            )
        }

        val week = buildStatsUiState(records, today, range = StatsRange.Week)
        val month = buildStatsUiState(records, today, range = StatsRange.Month)

        assertEquals(7, week.days.size)
        assertEquals(30, month.days.size)
        assertEquals(30, week.completedDays)
        assertEquals(week.completedDays, month.completedDays)
        assertEquals(week.totalDurationLabel, month.totalDurationLabel)
        assertEquals("21 小时", week.windowDurationLabel)
        assertEquals("90 小时", month.windowDurationLabel)
    }

    @Test
    fun selectedDayIsResolvedAndDroppedWhenOutOfWindow() {
        val records = listOf(
            CheckInRecord(windowStart, startMinuteOfDay = 420, endMinuteOfDay = 600),
            CheckInRecord(windowStart + 1, startMinuteOfDay = 430, endMinuteOfDay = 610),
        )

        val selected = buildStatsUiState(
            records = records,
            todayEpochDay = windowStart + 1,
            selectedEpochDay = windowStart,
        )
        assertEquals(windowStart, selected.selectedDay?.epochDay)
        assertEquals("07:00", selected.selectedDay?.startLabel)
        assertEquals("3 小时", selected.selectedDay?.durationLabel)

        val outside = buildStatsUiState(
            records = records,
            todayEpochDay = windowStart + 30,
            selectedEpochDay = windowStart,
        )
        assertNull(outside.selectedDay)
    }

    @Test
    fun dayCarriesPlanCountsAndTasksForTheDetailSheet() {
        val day = today - 1
        val state = buildStatsUiState(
            records = listOf(CheckInRecord(day, startMinuteOfDay = 420, endMinuteOfDay = 600)),
            todayEpochDay = today,
            plans = listOf(
                DailyPlan(
                    epochDay = day,
                    tasks = listOf(
                        PlanTask("a", "1", isCompleted = true),
                        PlanTask("b", "2", isCompleted = false),
                    ),
                ),
            ),
        )

        val wireDay = state.days.last { it.epochDay == day }
        assertEquals(2, wireDay.plannedCount)
        assertEquals(1, wireDay.completedCount)
        assertEquals(listOf("a", "b"), wireDay.tasks.map { it.title })
        assertTrue(wireDay.hasCheckIn)
    }

    @Test
    fun unfinishedRecordShowsStartButNotEndOrDuration() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 420, endMinuteOfDay = 600),
                CheckInRecord(windowStart + 1, startMinuteOfDay = 430),
            ),
            todayEpochDay = windowStart + 1,
        )

        assertEquals(2, state.startMinutes.filterNotNull().size)
        assertNull(state.days.last().endMinutes)
        assertNull(state.days.last().durationMinutes)
        assertEquals(1, state.completedDays)
        assertEquals(1, state.currentStreakDays)
    }

    @Test
    fun realStatsDoNotFillMissingDaysWithGeneratedRecords() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(epochDay = today, startMinuteOfDay = 420, endMinuteOfDay = 660),
            ),
            todayEpochDay = today,
        )

        assertEquals(1, state.startMinutes.filterNotNull().size)
        assertEquals(1, state.endMinutes.filterNotNull().size)
        assertEquals(1, state.days.count { it.hasCheckIn })
    }

    @Test
    fun emptyRecordsProduceTheEmptyState() {
        val state = buildStatsUiState(
            records = emptyList(),
            todayEpochDay = today,
        )

        assertEquals(0, state.completedDays)
        assertEquals("--:--", state.startSummary.averageValue)
        assertTrue(state.dayLabels.isEmpty())
        assertFalse(state.hasChartData)
    }

    @Test
    fun forgottenFinishShowsInAchievementsEvenWithoutRecords() {
        // 完成全部任务但忘记点结束：换日补记进成就值，
        // 但图区只画真实记录——没有时刻点，只给「暂无数据」提示。
        val state = buildStatsUiState(
            records = emptyList(),
            todayEpochDay = today,
            plans = listOf(
                DailyPlan(today - 1, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
        )

        assertEquals(1, state.completedDays)
        assertEquals(1, state.currentStreakDays)
        assertFalse(state.hasChartData)
        assertTrue(state.dayLabels.isNotEmpty())
    }

    @Test
    fun ganttAxisRoundsToHoursAndKeepsAPaddingSpan() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 430, endMinuteOfDay = 615),
            ),
            todayEpochDay = windowStart,
        )

        val axis = ganttAxisFor(state.days)

        // 07:10 向下取整到 07:00，10:15 向上取整到 11:00。
        assertEquals(420, axis.startMinute)
        assertEquals(660, axis.endMinute)
        assertEquals(240, axis.spanMinutes)
    }

    @Test
    fun ganttAxisWidensToATwoHourMinimumForTinySpans() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 600, endMinuteOfDay = 640),
            ),
            todayEpochDay = windowStart,
        )

        val axis = ganttAxisFor(state.days)

        // 10:00–10:40 只有 40 分钟，撑到两小时：10:00–12:00，并且把记录完整包住。
        assertEquals(120, axis.spanMinutes)
        assertEquals(600, axis.startMinute)
        assertEquals(720, axis.endMinute)
        assertTrue(axis.startMinute <= 600)
        assertTrue(axis.endMinute >= 640)
    }

    @Test
    fun ganttAxisGrowsLeftwardsWhenTheDayEndsNearMidnight() {
        // 深夜打卡：右端已经贴着 24:00，只能让起点往左退，不能反过来把记录切掉。
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 1400, endMinuteOfDay = 1430),
            ),
            todayEpochDay = windowStart,
        )

        val axis = ganttAxisFor(state.days)

        assertEquals(120, axis.spanMinutes)
        assertEquals(1440, axis.endMinute)
        assertEquals(1320, axis.startMinute)
        assertTrue(axis.startMinute <= 1400)
    }

    @Test
    fun ganttAxisEmptyWindowStillHasADrawableSpan() {
        val axis = ganttAxisFor(emptyList())

        assertEquals(0, axis.startMinute)
        assertTrue(axis.spanMinutes >= 60)
    }

    @Test
    fun ganttTicksSitOnWholeHoursAndCoverTheWindowEnd() {
        val axis = GanttAxis(startMinute = 420, endMinute = 1080)

        val week = ganttTimeTicks(axis, dayCount = 7)
        val month = ganttTimeTicks(axis, dayCount = 30)

        // 两端都要挂刻度：轴范围按整点取整，起点与末端都是整点，
        // 首尾标签各自对齐图表两侧，才不会和真实范围对不上。
        assertTrue(week.all { it % 60 == 0 })
        assertEquals(axis.startMinute, week.first())
        assertEquals(1080, week.last())
        assertEquals(1080, month.last())
        // 近 7 天每 2 小时、近 30 天每 4 小时一个刻度。
        // 只约束「不超过步长」：末尾那个刻度是硬补上的，它与前一个的间隔可能更小。
        assertTicksAscendWithinStep(week, stepMinutes = 120)
        assertTicksAscendWithinStep(month, stepMinutes = 240)
        assertTrue(month.size < week.size)
    }

    private fun assertTicksAscendWithinStep(ticks: List<Int>, stepMinutes: Int) {
        assertTrue("刻度必须严格递增：$ticks", ticks.zipWithNext().all { (a, b) -> b > a })
        assertTrue(
            "相邻刻度不应超过 $stepMinutes 分钟：$ticks",
            ticks.zipWithNext().all { (a, b) -> b - a <= stepMinutes },
        )
    }

    @Test
    fun ganttAxisFractionIsClampedAndMapsTheEnds() {
        val axis = GanttAxis(startMinute = 420, endMinute = 900)

        assertEquals(0f, axis.fractionOf(300f))
        assertEquals(1f, axis.fractionOf(1200f))
        assertEquals(0.5f, axis.fractionOf(660f))
    }

    @Test
    fun unfinishedTodayCarriesItsOngoingPositionForTheGanttBar() {
        val state = buildStatsUiState(
            records = listOf(
                CheckInRecord(windowStart, startMinuteOfDay = 420, endMinuteOfDay = 600),
                CheckInRecord(windowStart + 1, startMinuteOfDay = 430),
            ),
            todayEpochDay = windowStart + 1,
            nowMinuteOfDay = 540,
        )

        assertEquals(540f, state.days.last().ongoingEndMinutes)
        // 历史那天已经结束，不需要「此刻」，否则空心条会画到轴末端。
        assertNull(state.days.first { it.epochDay == windowStart }.ongoingEndMinutes)
    }

    @Test
    fun ongoingPositionIsOnlySetWhenTheClockIsProvided() {
        val state = buildStatsUiState(
            records = listOf(CheckInRecord(windowStart, startMinuteOfDay = 430)),
            todayEpochDay = windowStart,
        )

        assertNull(state.days.last().ongoingEndMinutes)
    }
}
