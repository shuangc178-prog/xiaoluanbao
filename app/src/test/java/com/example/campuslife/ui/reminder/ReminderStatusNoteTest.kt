package com.example.campuslife.ui.reminder

import com.example.campuslife.data.reminder.ExactAlarmCapability
import com.example.campuslife.data.reminder.NextAlarmOwnership
import com.example.campuslife.data.reminder.ReminderDelivery
import com.example.campuslife.data.reminder.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 「什么情况该说什么话」。
 *
 * 这一段是提醒功能的门面：用户判断「我的提醒到底靠不靠得住」，靠的就是这几行字。
 * 真机上要看全每一种分支得改系统设置、重启、等闹钟，成本太高，所以文案的判据在这里钉死。
 *
 * [buildReminderStatusNote] / [buildGeneralReminderStatusNote] 都是纯函数，
 * 不需要 Compose 运行时即可测试。
 */
class ReminderStatusNoteTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now(zone)

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun status(
        capability: ExactAlarmCapability = ExactAlarmCapability.Granted,
        notificationPermissionGranted: Boolean = true,
        batteryOptimizationIgnored: Boolean = true,
        nextAlarmAtMillis: Long? = at(19, 0),
        nextAlarmOwnership: NextAlarmOwnership = NextAlarmOwnership.Ours,
        overlayAllowed: Boolean = true,
        lastDelivery: ReminderDelivery? = null,
    ) = ReminderStatus(
        capability = capability,
        notificationPermissionGranted = notificationPermissionGranted,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        nextAlarmAtMillis = nextAlarmAtMillis,
        nextAlarmOwnership = nextAlarmOwnership,
        overlayAllowed = overlayAllowed,
        lastDelivery = lastDelivery,
    )

    @Test
    fun everythingHealthySaysOnTime() {
        val note = buildReminderStatusNote(
            status = status(),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals(ReminderNoteTone.Ok, note.tone)
        assertEquals("准时", note.label)
        assertTrue(note.detail.contains("系统里已排入下一条提醒"))
    }

    @Test
    fun aTimeThatAlreadyPassedIsCalledOutFirst() {
        // 判据顺序是刻意的：先看这条提醒本身还成不成立，再看系统让不让它弹。
        // 反过来说会出现「通知权限没开，但这条提醒其实早就过期了」这种答非所问的提示。
        val note = buildReminderStatusNote(
            status = status(notificationPermissionGranted = false),
            triggerAtMillis = at(8, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("时间已过", note.label)
        assertEquals(ReminderNoteTone.Warning, note.tone)
        assertNull(note.actionLabel)
    }

    @Test
    fun disabledNotificationsAreReportedBeforeSchedulingState() {
        val note = buildReminderStatusNote(
            status = status(notificationPermissionGranted = false),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("通知未开启", note.label)
        assertEquals("去开启", note.actionLabel)
    }

    @Test
    fun missingExactAlarmPermissionIsReportedAsPossibleDelay() {
        val note = buildReminderStatusNote(
            status = status(capability = ExactAlarmCapability.Missing),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("可能延迟", note.label)
        assertEquals("去授权", note.actionLabel)
        assertEquals(ReminderNoteAction.OpenExactAlarmSettings, note.action)
    }

    @Test
    fun deniedOverlayIsReportedWithAnEntryToThatPage() {
        // 没给悬浮窗权限时提醒照样震、照样进通知栏，只有「浮卡片」这一条静默失效——
        // 用户会以为功能坏了，所以要说出来并给一个能直接走到那一页的入口。
        val note = buildReminderStatusNote(
            status = status(overlayAllowed = false),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("不浮卡片", note.label)
        assertEquals("去开启", note.actionLabel)
        assertEquals(ReminderNoteAction.OpenOverlaySettings, note.action)
        assertTrue(note.detail.contains("震三下"))
    }

    @Test
    fun disabledNotificationsStillWinOverDeniedOverlay() {
        // 两个都坏掉时说最靠前的那个：通知没开时连通知栏那条都不会出现。
        val note = buildReminderStatusNote(
            status = status(notificationPermissionGranted = false, overlayAllowed = false),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("通知未开启", note.label)
        assertEquals(ReminderNoteAction.OpenNotificationSettings, note.action)
    }

    @Test
    fun aLostAlarmStillWinsOverDeniedOverlay() {
        // 顺序是刻意的：前面几条决定「提醒会不会响、准不准」，悬浮窗只决定「弹得好不好看」。
        val note = buildReminderStatusNote(
            status = status(
                nextAlarmAtMillis = null,
                nextAlarmOwnership = NextAlarmOwnership.None,
                overlayAllowed = false,
            ),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("未找到排程", note.label)
        assertEquals(ReminderNoteAction.Reschedule, note.action)
    }

    @Test
    fun deniedOverlayAlsoShowsUpInTheStandingSentence() {
        // 首页那一行走的是同一个判据（buildOutsideNote），不能只有任务弹窗里说。
        val note = buildGeneralReminderStatusNote(
            status = status(overlayAllowed = false),
            nowMillis = at(9, 0),
        )

        assertEquals("不浮卡片", note?.label)
        assertEquals(ReminderNoteAction.OpenOverlaySettings, note?.action)
    }

    @Test
    fun allowedOverlayStaysQuiet() {
        val note = buildReminderStatusNote(
            status = status(overlayAllowed = true),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("准时", note.label)
        assertNull(note.action)
    }

    @Test
    fun aLostAlarmRoundOffersToScheduleAgain() {
        val note = buildReminderStatusNote(
            status = status(nextAlarmAtMillis = null, nextAlarmOwnership = NextAlarmOwnership.None),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("未找到排程", note.label)
        assertEquals("重新排入", note.actionLabel)
    }

    @Test
    fun anotherAppsAlarmIsNotReportedAsALostSchedule() {        // 系统时钟排的闹钟会占住 getNextAlarmClock()，那不是本应用的排程丢了。
        val note = buildReminderStatusNote(
            status = status(nextAlarmAtMillis = at(6, 30), nextAlarmOwnership = NextAlarmOwnership.OtherApp),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("排程正常", note.label)
        assertNull(note.actionLabel)
    }

    @Test
    fun alarmCapabilityNotRequiredOnOldSystemsDoesNotWarn() {
        // Android 12 以下没有精确闹钟这一档，getNextAlarmClock() 也查不到，
        // 这时候不该报「未找到排程」——那是误报。
        val note = buildReminderStatusNote(
            status = status(
                capability = ExactAlarmCapability.NotRequired,
                nextAlarmAtMillis = null,
                nextAlarmOwnership = NextAlarmOwnership.None,
            ),
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals("准时", note.label)
    }

    @Test
    fun noTimeChosenFallsBackToTheGenericSentence() {
        val note = buildReminderStatusNote(
            status = status(),
            triggerAtMillis = null,
            nowMillis = at(9, 0),
        )

        assertEquals(ReminderNoteTone.Ok, note.tone)
    }

    @Test
    fun unknownStatusSaysItIsStillReadingInsteadOfGuessing() {
        val note = buildReminderStatusNote(
            status = null,
            triggerAtMillis = at(20, 0),
            nowMillis = at(9, 0),
        )

        assertEquals(ReminderNoteTone.Muted, note.tone)
        assertTrue(note.detail.contains("正在读取"))
    }

    @Test
    fun theStandingRowStaysSilentWhenEverythingIsFine() {
        // 用户口径（他是设计师）：正常时首页一个字都不说，不需要被解释一遍怎么用。
        // 所以这条常驻提示只在系统侧真的坏了时才返回内容，一切正常时返回 null、整行不占位置。
        // 连带代价：原来挂在同一句话后面的「上一次延迟 N 分钟」也没有界面了。
        val note = buildGeneralReminderStatusNote(
            status = status(
                lastDelivery = ReminderDelivery(
                    triggerAtMillis = at(8, 0),
                    deliveredAtMillis = at(8, 3),
                    title = "高等数学",
                ),
            ),
            nowMillis = at(9, 0),
        )

        assertNull(note)
    }

    @Test
    fun theStandingRowAlsoStaysSilentBeforeTheStatusIsRead() {
        // 状态还没读出来时也不占位置：首页不该为了「正在读取」先闪一行字。
        assertNull(buildGeneralReminderStatusNote(status = null, nowMillis = at(9, 0)))
    }

    @Test
    fun theStandingRowSpeaksUpWhenSomethingIsActuallyBroken() {
        val note = buildGeneralReminderStatusNote(
            status = status(notificationPermissionGranted = false),
            nowMillis = at(9, 0),
        )

        assertEquals("通知未开启", note?.label)
        assertEquals(ReminderNoteAction.OpenNotificationSettings, note?.action)
    }
}
