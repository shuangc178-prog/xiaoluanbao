package com.example.campuslife.data.checkin

import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import com.example.campuslife.ui.stats.currentStreak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 「已打卡」的口径（产品文档第 16.17 节）：
 *
 * - 点了结束打卡（记录带结束时间）→ 已打卡；
 * - 完成全部任务但忘记点结束 → 该日变成过去后，由计划存档自动补记为已打卡；
 * - 「未打卡」只剩两种：当天没结束打卡且任务没勾完，或当天没有安排任务；
 * - 当天任务全勾但还没点结束时不提前计：等结束打卡或换日才落定。
 */
class CheckInCompletionTest {

    private val today: Long = LocalDate.of(2026, 9, 14).toEpochDay()

    @Test
    fun finishedRecordsCountAsCheckedIn() {
        val days = completedCheckInDays(
            records = listOf(
                CheckInRecord(today, startMinuteOfDay = 420, endMinuteOfDay = 600),
            ),
            plans = emptyList(),
            todayEpochDay = today,
        )

        assertEquals(setOf(today), days)
    }

    @Test
    fun forgottenFinishIsRecoveredFromThePlanArchive() {
        // 开始了、任务全勾、但没点结束：换日后由存档补记。
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = listOf(CheckInRecord(yesterday, startMinuteOfDay = 420)),
            plans = listOf(
                DailyPlan(yesterday, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(setOf(yesterday), days)
    }

    @Test
    fun forgottenFinishCountsEvenWithoutAnyRecord() {
        // 连开始打卡都没点过：任务全勾 + 该日已过，同样补记。
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = emptyList(),
            plans = listOf(
                DailyPlan(yesterday, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(setOf(yesterday), days)
    }

    @Test
    fun todaysCompletedTasksDoNotCountUntilFinishedOrRollover() {
        // 今天全勾但没点结束：还没落定，不算（等用户点结束或等到明天）。
        val days = completedCheckInDays(
            records = listOf(CheckInRecord(today, startMinuteOfDay = 420)),
            plans = listOf(
                DailyPlan(today, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(emptySet<Long>(), days)
    }

    @Test
    fun startedButUnfinishedRecordWithIncompleteTasksDoesNotCount() {
        // 开始了但任务没勾完，也没点结束：未打卡（点击按钮不单独成立）。
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = listOf(CheckInRecord(yesterday, startMinuteOfDay = 420)),
            plans = listOf(
                DailyPlan(
                    yesterday,
                    listOf(
                        PlanTask("a", "1", isCompleted = true),
                        PlanTask("b", "2", isCompleted = false),
                    ),
                ),
            ),
            todayEpochDay = today,
        )

        assertEquals(emptySet<Long>(), days)
    }

    @Test
    fun daysWithoutTasksNeverCount() {
        // 当天未安排打卡任务：未打卡，即使过去了也不补记。
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = emptyList(),
            plans = listOf(DailyPlan(yesterday, emptyList())),
            todayEpochDay = today,
        )

        assertEquals(emptySet<Long>(), days)
    }

    @Test
    fun recordAndPlanDoNotDoubleCountTheSameDay() {
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = listOf(
                CheckInRecord(yesterday, startMinuteOfDay = 420, endMinuteOfDay = 600),
            ),
            plans = listOf(
                DailyPlan(yesterday, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(setOf(yesterday), days)
    }

    @Test
    fun futurePlansDoNotCount() {
        // 明天就把任务提前勾完（明日安排）：不算今天或明天的打卡。
        val days = completedCheckInDays(
            records = emptyList(),
            plans = listOf(
                DailyPlan(today + 1, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(emptySet<Long>(), days)
    }

    @Test
    fun streakRunsThroughAForgottenFinishDay() {
        // 前天任务全勾但忘了点结束，昨天正常打了卡：连续不断。
        val dayBefore = today - 2
        val yesterday = today - 1
        val days = completedCheckInDays(
            records = listOf(
                CheckInRecord(yesterday, startMinuteOfDay = 420, endMinuteOfDay = 600),
            ),
            plans = listOf(
                DailyPlan(dayBefore, listOf(PlanTask("a", "1", isCompleted = true))),
            ),
            todayEpochDay = today,
        )

        assertEquals(setOf(dayBefore, yesterday), days)
        // 今天还没打：连续从昨天起算 = 2。
        assertEquals(2, currentStreak(days.toList(), today))
    }
}
