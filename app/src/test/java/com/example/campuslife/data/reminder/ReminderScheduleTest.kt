package com.example.campuslife.data.reminder

import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒排程的规则。
 *
 * 这一层是纯函数，不碰 AlarmManager 也不碰存储，所以「什么时候该弹、什么时候不该弹」
 * 可以完全靠单元测试钉住——真机上跑一次闹钟的成本太高了。
 *
 * 日历事件已经回滚，提醒现在只有「任务预期开始时间」一个来源。
 *
 * **日期一律以「今天」为锚，不写死年月日。** 早先这里写死 2026-09-16，而机器上真实的当天
 * 已经是 09-19，那些「过去 / 未来」的期望就整体错了位，看起来像功能坏了。
 * 排程依赖「现在」，所以参照时刻必须跟着真实当天走。
 */
class ReminderScheduleTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now(zone)
    private val epochDay: Long = day.toEpochDay()

    /** 排程的参照时刻统一取当天 0 点：这一天还没开始，所有钟点都还没到。 */
    private val dayStart: Long = LocalDateTime.of(day, LocalTime.MIDNIGHT)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun millisAt(hour: Int, minute: Int): Long =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun millisAt(daysAhead: Long, hour: Int, minute: Int): Long =
        LocalDateTime.of(day.plusDays(daysAhead), LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private fun candidatesOf(plans: List<DailyPlan>) =
        buildReminderCandidates(plans, checkInEpochDay = epochDay, zone = zone, nowMillis = dayStart)

    @Test
    fun taskExpectedStartTimeBecomesCandidate() {
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(
                PlanTask("上肢力量训练", "学校健身房 · 35 分钟", expectedStartMinute = 19 * 60),
                PlanTask("没有排时间的任务", ""),
            ),
        )

        val candidates = candidatesOf(listOf(plan))

        assertEquals(1, candidates.size)
        assertEquals("上肢力量训练", candidates.first().title)
        assertEquals(millisAt(19, 0), candidates.first().triggerAtMillis)
    }

    @Test
    fun completedTasksDoNotRemind() {
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(
                PlanTask("已经勾掉的任务", "", isCompleted = true, expectedStartMinute = 9 * 60),
            ),
        )

        assertTrue(candidatesOf(listOf(plan)).isEmpty())
    }

    @Test
    fun deliveredTokensAreNotScheduledAgain() {
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("上肢力量训练", "", expectedStartMinute = 8 * 60 + 30)),
        )
        val candidates = candidatesOf(listOf(plan))
        val delivered = candidates.mapTo(mutableSetOf()) { it.token }

        val pending = pendingReminders(
            candidates = candidates,
            deliveredTokens = delivered,
            nowMillis = dayStart,
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun slightlyLateRemindersAreKeptSoAlarmsAreNotCancelledEarly() {
        // 闹钟可能因为省电策略晚到几分钟。这段时间里如果应用被重新拉起就把闹钟取消，
        // 用户就永远收不到这条提醒了，所以要保留。
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("上肢力量训练", "", expectedStartMinute = 8 * 60)),
        )
        val candidates = candidatesOf(listOf(plan))

        val pending = pendingReminders(
            candidates = candidates,
            deliveredTokens = emptySet(),
            nowMillis = millisAt(8, 5),
        )

        assertEquals(1, pending.size)
    }

    @Test
    fun longMissedRemindersAreDroppedInsteadOfFiringLate() {
        // 隔了两天才打开应用：连「次日同一钟点」都已经是过去，锚定就不再多推，
        // 保留原时刻交给 grace 丢掉——早上不该被一串过期通知轰炸。
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("上肢力量训练", "", expectedStartMinute = 8 * 60)),
        )
        val twoDaysLater = millisAt(2, 9, 0)

        val candidates = buildReminderCandidates(
            plans = listOf(plan),
            checkInEpochDay = epochDay,
            zone = zone,
            nowMillis = twoDaysLater,
        )

        assertEquals(millisAt(8, 0), candidates.single().triggerAtMillis)

        val pending = pendingReminders(
            candidates = candidates,
            deliveredTokens = emptySet(),
            nowMillis = twoDaysLater,
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun tokenChangesWhenTheReminderIsMovedToAnotherTime() {
        // 弹过通知后用户把提醒改到别的钟点，新的一次要能正常弹出来。
        fun candidateAt(hour: Int) = candidatesOf(
            listOf(
                DailyPlan(
                    epochDay = epochDay,
                    tasks = listOf(PlanTask("上肢力量训练", "", expectedStartMinute = hour * 60)),
                ),
            ),
        ).single()

        val atEight = candidateAt(8)
        val atTen = candidateAt(10)

        assertFalse(atEight.token == atTen.token)
        assertEquals(
            1,
            pendingReminders(listOf(atTen), setOf(atEight.token), millisAt(9, 0)).size,
        )
    }

    @Test
    fun candidatesAreSortedByTriggerTime() {
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(
                PlanTask("早上的任务", "", expectedStartMinute = 7 * 60),
                PlanTask("傍晚的任务", "", expectedStartMinute = 18 * 60),
                PlanTask("晚上的任务", "", expectedStartMinute = 20 * 60),
            ),
        )
        val candidates = candidatesOf(listOf(plan))

        assertEquals(
            listOf(millisAt(7, 0), millisAt(18, 0), millisAt(20, 0)),
            candidates.map { it.triggerAtMillis },
        )
    }

    @Test
    fun lateNightReminderIsAnchoredToTheSameCalendarDay() {
        // 凌晨 00:10：打卡日还是**昨天**，而计划表头写的是今天。用户给 01:30 的任务
        // 设提醒，字面意思是「约 80 分钟后」。按计划日期硬算本来也落在今天凌晨，
        // 但换日边界上「计划的日期比打卡日晚一天」会让按打卡日算的那条路径彻底错一天，
        // 所以这条用例同时钉住「锚定后仍然是今天凌晨 01:30」。
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("夜间任务", "", expectedStartMinute = 90)),
        )
        val nowAtTenPastMidnight = millisAt(0, 10)

        val candidates = buildReminderCandidates(
            plans = listOf(plan),
            checkInEpochDay = epochDay - 1,
            zone = zone,
            nowMillis = nowAtTenPastMidnight,
        )

        assertEquals(1, candidates.size)
        assertEquals(millisAt(1, 30), candidates.first().triggerAtMillis)
    }

    @Test
    fun anAlreadyPassedTimeOnTodaysPlanIsNotResurrectedTomorrow() {
        // 参照时刻取当天 11 点，计划就是当天（打卡日也是当天）：8 点这条已经错过。
        // 锚定**不该**把它推到次日 8 点——那会把「今早忘了做的事」凭空变成
        // 「明天早上 8 点还有一条提醒」，与用户排的时间已经不是一回事。
        // 正确做法是保留原时刻，交给 grace 判定丢弃。
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("上肢力量训练", "", expectedStartMinute = 8 * 60)),
        )

        val candidates = buildReminderCandidates(
            plans = listOf(plan),
            checkInEpochDay = epochDay,
            zone = zone,
            nowMillis = millisAt(11, 0),
        )

        assertEquals(millisAt(8, 0), candidates.single().triggerAtMillis)
    }

    @Test
    fun lateNightPlanSeenTheNextMorningIsDroppedAsStale() {
        // 同一份凌晨 01:30 的计划，到第二天上午 10 点才被看到：它已经错过，
        // 而且计划日期并不比打卡日晚一天（已同一天），所以不挪动，交给 grace 丢掉。
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(PlanTask("夜间任务", "", expectedStartMinute = 90)),
        )

        val pending = pendingReminders(
            candidates = buildReminderCandidates(
                plans = listOf(plan),
                checkInEpochDay = epochDay,
                zone = zone,
                nowMillis = millisAt(10, 0),
            ),
            deliveredTokens = emptySet(),
            nowMillis = millisAt(10, 0),
        )

        assertTrue(pending.isEmpty())
    }
}
