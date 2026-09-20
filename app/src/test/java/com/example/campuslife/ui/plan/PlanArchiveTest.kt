package com.example.campuslife.ui.plan

import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 每日计划存档页的周分组口径。
 *
 * 存档页按「有记录的周」建分组，每周固定铺 7 个日期气泡——
 * 因此存档数量可以一直长，但不会把任务直接堆成长列表；
 * 缺计划的那天也要占一格（气泡变灰、点进去显示没有存档）。
 */
class PlanArchiveTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val todayEpochDay: Long = today.toEpochDay()

    private fun plan(epochDay: Long, completed: Int = 1) = DailyPlan(
        epochDay = epochDay,
        tasks = (0 until 2).map { index ->
            PlanTask(
                title = "任务${index + 1}",
                supportingText = "",
                isCompleted = index < completed,
            )
        },
    )

    @Test
    fun emptyPlansProduceNoWeeks() {
        assertTrue(buildPlanArchiveWeeks(emptyList()).isEmpty())
    }

    @Test
    fun everyWeekAlwaysHasSevenClickableDays() {
        val weeks = buildPlanArchiveWeeks(listOf(plan(todayEpochDay)))
        val week = weeks.single()
        assertEquals(7, week.days.size)
        // 周一到周日，七天连续。
        assertEquals(
            (0L..6L).map { week.weekStartEpochDay + it },
            week.days.map { it.epochDay },
        )
        // 只有第一天有计划，其余六天占位可点（plan 为 null）。
        assertEquals(1, week.recordedDayCount)
        assertEquals(6, week.days.count { it.plan == null })
    }

    @Test
    fun weekStartsOnMonday() {
        val weeks = buildPlanArchiveWeeks(listOf(plan(todayEpochDay)))
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(weeks.single().weekStartEpochDay).dayOfWeek)
    }

    @Test
    fun hundredDaysAreNotTruncated() {
        // 连续 100 天都有存档：按周切片全部保留，一周都不少。
        val plans = (0 until 100).map { plan(todayEpochDay - it) }
        val weeks = buildPlanArchiveWeeks(plans)
        assertTrue(weeks.size >= 15)
        assertEquals(100, weeks.sumOf { it.recordedDayCount })
    }

    @Test
    fun weeksAreNewestFirst() {
        val plans = (0 until 20).map { plan(todayEpochDay - it) }
        val weeks = buildPlanArchiveWeeks(plans)
        assertEquals(
            weeks.map { it.weekStartEpochDay }.sortedDescending(),
            weeks.map { it.weekStartEpochDay },
        )
    }

    @Test
    fun missingDayIsStillClickableWithNoPlan() {
        // 同一周里跳一天存档（周三有、周二空）：缺的那天照样占一格，只是没有计划。
        val wednesday = today.minusDays((today.dayOfWeek.value - DayOfWeek.WEDNESDAY.value).toLong())
        val tuesday = wednesday.minusDays(1)
        val thursday = wednesday.plusDays(1)
        val plans = listOf(plan(wednesday.toEpochDay()), plan(thursday.toEpochDay()))

        val days = buildPlanArchiveWeeks(plans).first().days
        val gap = days.first { it.epochDay == tuesday.toEpochDay() }
        assertNull(gap.plan)
        // 它仍在分组里，因此界面上仍是一枚可点的气泡。
        assertTrue(days.any { it.epochDay == gap.epochDay })
        assertEquals(DayOfWeek.TUESDAY, gap.date.dayOfWeek)
    }

    @Test
    fun daysCarryTheirPlanForTheDetailView() {
        val plans = listOf(plan(todayEpochDay, completed = 2))
        val day = buildPlanArchiveWeeks(plans).first().days
            .first { it.epochDay == todayEpochDay }
        assertEquals(2, day.plan?.completedCount)
        assertTrue(day.plan?.isAllCompleted == true)
        assertEquals(today, day.date)
    }
}
