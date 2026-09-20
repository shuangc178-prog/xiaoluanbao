package com.example.campuslife.data.reminder

import com.example.campuslife.data.checkin.formatMinuteOfDay
import com.example.campuslife.data.plan.DailyPlan
import java.time.ZoneId

/**
 * 一条真正要交给 AlarmManager 的提醒。
 *
 * 目前唯一的来源是「任务的预期开始时间」；日历事件已经回滚掉。
 */
data class ScheduledReminder(
    /** 稳定标识：来源 + 日期 + 时刻（+ 标题）。日期与时刻进 key，改时间就等于换了一条提醒。 */
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
) {
    /**
     * 触发用的唯一凭据，也是「已经弹过」的存档记录。
     *
     * 带上触发时刻是刻意的：通知弹过之后，用户把同一条提醒改到别的钟点，
     * token 就变了，新的那次还能正常弹出来，而旧的那次不会被重复触发。
     */
    val token: String get() = "$triggerAtMillis|$key"

    /** PendingIntent 的 requestCode 与通知 id 都用它。 */
    val requestCode: Int get() = key.hashCode() and 0x7FFFFFFF
}

/**
 * 从 token 里取回排程时的触发时刻。
 *
 * token 的第一段就是它。通知弹出时要靠这个值与实际弹出时刻做差算出真实延迟，
 * 所以解析口径写在这里，和 [ScheduledReminder.token] 放在一起，
 * 两边不会各写一份而悄悄对不上。
 */
internal fun scheduledAtOfToken(token: String): Long? =
    token.substringBefore('|').toLongOrNull()

/** 错过太久的提醒不再补弹：进程被系统回收过夜后，早上不该被一串过期通知轰炸。 */
const val REMINDER_MISFIRE_GRACE_MILLIS = 15L * 60 * 1000

/**
 * 把计划任务里的「预期开始时间」汇总成候选提醒。
 *
 * 纯函数，不碰存储也不碰系统闹钟——排程规则因此可以单独写单元测试。
 *
 * [checkInEpochDay] 是当前的**打卡日**编号（首页那把凌晨 6 点的尺子）。它参与的是换日锚定：
 * 00:00–05:59 之间「今天」的计划还带着前一天的日期，用户设的 01:30 按日期硬算会变成
 * 过去时刻、永远不响。见 [anchorReminderTriggerAtMillis]。
 */
fun buildReminderCandidates(
    plans: List<DailyPlan>,
    checkInEpochDay: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<ScheduledReminder> = plans.flatMap { plan ->
    plan.tasks.mapNotNull { task ->
        val minute = task.expectedStartMinute ?: return@mapNotNull null
        // 已经勾掉的任务不再提醒：用户已经把这件事处理完了。
        if (task.isCompleted || task.title.isBlank()) return@mapNotNull null
        ScheduledReminder(
            key = "task|${plan.epochDay}|$minute|${task.title}",
            triggerAtMillis = anchorReminderTriggerAtMillis(
                epochDay = plan.epochDay,
                minuteOfDay = minute,
                nowMillis = nowMillis,
                checkInEpochDay = checkInEpochDay,
                zone = zone,
            ) ?: return@mapNotNull null,
            title = task.title,
            body = task.supportingText.ifBlank {
                "任务预期开始时间 ${formatMinuteOfDay(minute)}"
            },
        )
    }
}.sortedBy { it.triggerAtMillis }

/**
 * 从候选里挑出真正还需要排闹钟的那些。
 *
 * 两道过滤：
 * 1. [deliveredTokens] 里的已经弹过通知，不重复弹——这是「只在到时间时弹一次」的兜底；
 * 2. 早于 `now - misfireGrace` 的已经错过太久，直接放弃。
 *
 * 注意 grace 之内的过去时刻要**保留**：闹钟可能因为省电策略晚到几分钟，
 * 这期间如果应用被重新拉起就把闹钟取消了，用户就永远收不到这条提醒。
 */
fun pendingReminders(
    candidates: List<ScheduledReminder>,
    deliveredTokens: Set<String>,
    nowMillis: Long,
    misfireGraceMillis: Long = REMINDER_MISFIRE_GRACE_MILLIS,
): List<ScheduledReminder> {
    val earliestAccepted = nowMillis - misfireGraceMillis
    return candidates.filter { candidate ->
        candidate.token !in deliveredTokens && candidate.triggerAtMillis >= earliestAccepted
    }
}
