package com.example.campuslife.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒自检与时刻锚定的口径。
 *
 * 这一层同样不碰 AlarmManager：系统读到什么由 [ReminderSystemState] 传进来，
 * 于是「闹钟还算不算数」「该说哪句话」都能在这里钉死。
 * 真机上要验一次状态得改系统设置、重启、等闹钟，成本太高，只能靠这些用例兜住。
 *
 * **日期一律以「今天」为锚，不写死年月日。** 这一条踩过坑：早先这里写死 2026-09-16，
 * 而机器上真实的当天已经是 09-19，于是用例里那个「现在」其实是两天前，
 * 「当天更早的钟点顺延到次日」这种期望全部不成立，表现成功能坏了。
 * 锚定逻辑本身依赖「现在」，所以参照时刻必须跟着真实当天走。
 */
class ReminderStatusTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now(zone)
    private val epochDay: Long = day.toEpochDay()

    private val ownPackage = "io.github.chengyishuanger.campuslife"
    private val otherPackage = "com.android.deskclock"

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun atDayOffset(days: Long, hour: Int, minute: Int): Long =
        LocalDateTime.of(day.plusDays(days), LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private fun show(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toString()

    private fun okSystemState(
        nextAlarmAtMillis: Long?,
        creatorPackage: String? = ownPackage,
    ) = ReminderSystemState(
        capability = ExactAlarmCapability.Granted,
        notificationPermissionGranted = true,
        batteryOptimizationIgnored = true,
        systemNextAlarmAtMillis = nextAlarmAtMillis,
        systemNextAlarmCreatorPackage = creatorPackage,
    )

    @Test
    fun ownAlarmIsRecognisedAsOurs() {
        val status = buildReminderStatus(
            system = okSystemState(at(19, 0)),
            ownPackageName = ownPackage,
            lastDelivery = null,
        )

        assertEquals(NextAlarmOwnership.Ours, status.nextAlarmOwnership)
        assertEquals(at(19, 0), status.nextAlarmAtMillis)
    }

    @Test
    fun anotherAppsAlarmIsNotMistakenForOurs() {
        // 状态栏上的闹钟图标可能来自系统时钟。不判断归属就会把别人的闹钟
        // 当成本应用的排程还在，报出假的安心。
        val status = buildReminderStatus(
            system = okSystemState(at(6, 30), creatorPackage = otherPackage),
            ownPackageName = ownPackage,
            lastDelivery = null,
        )

        assertEquals(NextAlarmOwnership.OtherApp, status.nextAlarmOwnership)
    }

    @Test
    fun missingAlarmClockMeansNone() {
        val status = buildReminderStatus(
            system = okSystemState(nextAlarmAtMillis = null, creatorPackage = null),
            ownPackageName = ownPackage,
            lastDelivery = null,
        )

        assertEquals(NextAlarmOwnership.None, status.nextAlarmOwnership)
        assertNull(status.nextAlarmAtMillis)
    }

    @Test
    fun todayAndTomorrowAreNamedInsteadOfDated() {
        assertEquals(
            "今天 08:30",
            formatReminderMoment(at(8, 30), nowMillis = at(7, 0), zone = zone),
        )
        // 真正「明天」的提醒：日期往后一天，参照时刻仍在今天。
        assertEquals(
            "明天 08:30",
            formatReminderMoment(atDayOffset(1, 8, 30), nowMillis = at(7, 0), zone = zone),
        )
        // 再远的按「月日 时刻」写，日期跟着真实当天走，不写死。
        val far = day.plusDays(10)
        assertEquals(
            "${far.monthValue}月${far.dayOfMonth}日 08:30",
            formatReminderMoment(atDayOffset(10, 8, 30), nowMillis = at(7, 0), zone = zone),
        )
    }

    @Test
    fun twoDaysAheadIsLabelledWithItsDate() {
        // 不引入「后天」这种说法，再远的一律写月日，少一层换算。
        val expected = LocalDate.now(zone).plusDays(2)
        assertEquals(
            "${expected.monthValue}月${expected.dayOfMonth}日 08:30",
            formatReminderMoment(atDayOffset(2, 8, 30), nowMillis = at(7, 0), zone = zone),
        )
    }

    @Test
    fun sameDayButEarlierHourIsNotCalledTomorrow() {
        // 只比日期，不比时刻：今天 07:00 相对今天 09:00 仍然是「今天」（已经过去了）。
        assertEquals("今天 07:00", formatReminderMoment(at(7, 0), nowMillis = at(9, 0), zone = zone))
    }

    @Test
    fun secondsOfLagCountAsOnTime() {
        // 秒级偏差用户感知不到，报成「延迟 3 秒」只会让人以为出了问题。
        assertEquals("准时", formatReminderLag(0))
        assertEquals("准时", formatReminderLag(45_000))
        assertEquals("准时", formatReminderLag(59_999))
    }

    @Test
    fun minutesOfLagAreReported() {
        assertEquals("延迟 1 分钟", formatReminderLag(60_000))
        assertEquals("延迟 3 分钟", formatReminderLag(3 * 60_000))
        assertEquals("延迟 1 小时 5 分钟", formatReminderLag(65 * 60_000))
    }

    @Test
    fun negativeLagIsClampedSoClockRollbackDoesNotReadAsNegative() {
        assertEquals("准时", formatReminderLag(-5_000))
    }

    @Test
    fun triggerTimeIsAnchoredToTheNextOccurrenceOfThatHour() {
        // 凌晨 00:10：打卡日仍是**昨天**，而计划表头写的是今天。用户设 01:30，
        // 字面意思是「约 80 分钟后」，也就是今天凌晨 01:30。
        val nowAtTenPastMidnight = LocalDateTime.of(day, LocalTime.of(0, 10))
            .atZone(zone).toInstant().toEpochMilli()

        val anchored = anchorReminderTriggerAtMillis(
            epochDay = epochDay,
            minuteOfDay = 90,
            nowMillis = nowAtTenPastMidnight,
            checkInEpochDay = epochDay - 1,
            zone = zone,
        )

        assertEquals(at(1, 30), anchored)
    }

    /**
     * 这条是整个锚定逻辑的**不变量**：换日锚定只在「计划日期正好比打卡日晚一天」时
     * 才会把时刻挪到计划的当天，而且挪过去的那个时刻必须还没到
     * （否则它就落进 grace 窗口，仍然会被照常判定，不会被凭空提前）。
     *
     * 曾经的实现按计划日期硬算，凌晨设的钟点会落到 23 小时之前 —— 一条「还没到期」的提醒
     * 被算成了过去时刻，然后被 grace 丢掉，永远不响。断言写成「不可回退到过去」，
     * 就是为了让这种退化无论从哪个入口发生都会红。
     */
    @Test
    fun anchoringOnlyMovesTheMomentOntoTheCheckInDay() {
        val nowAtTenPastMidnight = LocalDateTime.of(day, LocalTime.of(0, 10))
            .atZone(zone).toInstant().toEpochMilli()

        // 0:10 看「今天日期 + 01:30」，打卡日还是昨天：落到今天凌晨 01:30，还没到。
        val morning = anchorReminderTriggerAtMillis(
            epochDay = epochDay,
            minuteOfDay = 90,
            nowMillis = nowAtTenPastMidnight,
            checkInEpochDay = epochDay - 1,
            zone = zone,
        )!!
        assertTrue("锚定结果落在了过去：${show(morning)}", morning >= nowAtTenPastMidnight)
        assertEquals(at(1, 30), morning)

        // 同样在 0:10，但计划已经是「昨天」的（打卡日与之相同、正常隔天情形）：
        // 那一刻确实是过去，不挪动，交给 grace 判定。
        assertEquals(
            atDayOffset(-1, 1, 30),
            anchorReminderTriggerAtMillis(
                epochDay = epochDay - 1,
                minuteOfDay = 90,
                nowMillis = nowAtTenPastMidnight,
                checkInEpochDay = epochDay - 1,
                zone = zone,
            ),
        )

        // 当天 19:00 的计划在上午 9 点看：本来就没过去，不该被挪动。
        assertEquals(
            at(19, 0),
            anchorReminderTriggerAtMillis(
                epochDay = epochDay,
                minuteOfDay = 19 * 60,
                nowMillis = at(9, 0),
                checkInEpochDay = epochDay,
                zone = zone,
            ),
        )
    }

    @Test
    fun aPlanMoreThanADayBehindIsNotResurrectedIntoTomorrow() {
        // 计划的日期比打卡日早两天以上：陈旧计划。既不能往后挪（会把「前天早上 8 点的事」
        // 变成「明天早上 8 点」这种凭空多出来的提醒），也不能被算成还没到——
        // 保持原时刻（过去），由 grace 丢掉。
        val anchored = anchorReminderTriggerAtMillis(
            epochDay = epochDay - 2,
            minuteOfDay = 8 * 60,
            nowMillis = at(10, 0),
            checkInEpochDay = epochDay,
            zone = zone,
        )

        assertEquals(atDayOffset(-2, 8, 0), anchored)
    }

    @Test
    fun aTimeMoreThanADayBehindStaysWhereItIsSoGraceCanDropIt() {
        // 同一条当天计划，但参照时刻已经是两天后：连「次日同一钟点」也过去了，
        // 这时不再往后推，保留原时刻交给 grace 丢弃。
        val twoDaysLater = atDayOffset(2, 9, 0)

        val anchored = anchorReminderTriggerAtMillis(
            epochDay = epochDay,
            minuteOfDay = 8 * 60,
            nowMillis = twoDaysLater,
            checkInEpochDay = epochDay + 2,
            zone = zone,
        )

        assertEquals(at(8, 0), anchored)
    }

    @Test
    fun outOfRangeMinuteIsRejectedInsteadOfRollingIntoTheNextDay() {
        assertNull(reminderTriggerAtMillis(epochDay, 24 * 60, zone))
        assertNull(reminderTriggerAtMillis(epochDay, -1, zone))
        assertNull(
            anchorReminderTriggerAtMillis(
                epochDay = epochDay,
                minuteOfDay = 24 * 60,
                nowMillis = at(9, 0),
                checkInEpochDay = epochDay,
                zone = zone,
            ),
        )
    }
}
