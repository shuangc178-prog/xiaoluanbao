package com.example.campuslife.data.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 精确闹钟权限的实际能力档位。
 *
 * 分档而不是布尔值，是因为「没有权限」有两种完全不同的后果，提示语不能混着写：
 * 一种能引导用户去开，另一种这台系统上根本开不了。
 */
enum class ExactAlarmCapability {
    /** 精确闹钟可用：走 `setAlarmClock`，系统当用户自己的闹钟对待，不参与批处理。 */
    Granted,

    /**
     * 拿不到精确闹钟授权：退回 [ReminderAlarms] 的降级链，
     * 低电耗空闲下可能被推迟十几分钟。这是「提醒不准」最常见的原因，必须让用户看见。
     */
    Missing,

    /** 系统版本低于 Android 12，本来就没有精确闹钟这一档，走 `setAlarmClock` 即可。 */
    NotRequired,
}

/** 系统里查到的那条用户可见闹钟，是不是本应用排的。 */
enum class NextAlarmOwnership {
    /** 是本应用排的——排程确实还活着。 */
    Ours,

    /** 系统里有闹钟，但是别的应用（比如系统时钟）排的，与本应用无关。 */
    OtherApp,

    /** 系统里一条用户可见闹钟都没有：本应用的排程很可能已经被清掉了。 */
    None,
}

/**
 * 一次弹出通知的实况：计划什么时候弹、实际什么时候弹。
 *
 * [lagMillis] 就是「准不准时」这个问题的直接答案，也是验证排程改动有没有效果的唯一依据。
 * 负数极少见，但确实可能出现（系统时间在排程后被往回调），不做 clamp，如实呈现。
 */
data class ReminderDelivery(
    val triggerAtMillis: Long,
    val deliveredAtMillis: Long,
    val title: String,
) {
    val lagMillis: Long get() = deliveredAtMillis - triggerAtMillis
}

/**
 * 提醒子系统的运行时状态快照，供界面自检那一行使用。
 *
 * 它是**读出来**的，不是记下来的：闹钟在不在以系统为准（见 [ReminderSystemStateReader]），
 * 所以系统把闹钟静默清掉这种「应用收不到任何通知」的情况也能被看见。
 */
data class ReminderStatus(
    val capability: ExactAlarmCapability,
    val notificationPermissionGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val nextAlarmAtMillis: Long?,
    val nextAlarmOwnership: NextAlarmOwnership,
    /**
     * 用户给没给「显示在其他应用上层」（悬浮窗）权限——决定到点那张卡片能不能浮出来。
     *
     * 这是一项特殊权限，只能在系统设置里授予（MIUI 上叫「悬浮窗」），
     * 而且**没给时一切照旧**：震三下、通知栏一条都在，只是不浮卡片。
     * 所以它单独一档说出来，而不是混进「通知未开启」里——两者的修法完全不同。
     */
    val overlayAllowed: Boolean = true,
    val lastDelivery: ReminderDelivery?,
) {
    companion object {
        /** 还没有读到真实状态时的占位，界面据此显示空态而不是撒谎。 */
        val Unknown = ReminderStatus(
            capability = ExactAlarmCapability.NotRequired,
            notificationPermissionGranted = true,
            batteryOptimizationIgnored = true,
            nextAlarmAtMillis = null,
            nextAlarmOwnership = NextAlarmOwnership.None,
            overlayAllowed = true,
            lastDelivery = null,
        )
    }
}

/** 系统侧读到的原始事实，[ReminderSystemStateReader] 的返回值。 */
data class ReminderSystemState(
    val capability: ExactAlarmCapability,
    val notificationPermissionGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val systemNextAlarmAtMillis: Long?,
    val systemNextAlarmCreatorPackage: String?,
    /** 见 [ReminderStatus.overlayAllowed]；默认 true 只是为了老调用方，读取方一定会传真实值。 */
    val overlayAllowed: Boolean = true,
)

/**
 * 把「系统侧事实 + 存档里的弹出记录」拼成界面要的状态。
 *
 * 纯函数：系统读取和 DataStore 读取都在外面做完，这里只做判定，
 * 所以「闹钟还算不算数」这条口径可以完全靠单元测试钉住。
 */
fun buildReminderStatus(
    system: ReminderSystemState,
    ownPackageName: String,
    lastDelivery: ReminderDelivery?,
): ReminderStatus {
    val ownership = when (system.systemNextAlarmAtMillis) {
        null -> NextAlarmOwnership.None
        else -> if (system.systemNextAlarmCreatorPackage == ownPackageName) {
            NextAlarmOwnership.Ours
        } else {
            NextAlarmOwnership.OtherApp
        }
    }
    return ReminderStatus(
        capability = system.capability,
        notificationPermissionGranted = system.notificationPermissionGranted,
        batteryOptimizationIgnored = system.batteryOptimizationIgnored,
        nextAlarmAtMillis = system.systemNextAlarmAtMillis,
        nextAlarmOwnership = ownership,
        overlayAllowed = system.overlayAllowed,
        lastDelivery = lastDelivery,
    )
}

/** 「下次提醒」和「上次弹出」都要按用户读得懂的钟点呈现，两处共用一个格式。 */
private val reminderTimeFormatter = DateTimeFormatter.ofPattern(
    "M月d日 HH:mm",
    Locale.SIMPLIFIED_CHINESE,
)

/**
 * 把触发时刻格式化成「今天 08:30 / 明天 08:30 / 9月16日 08:30」。
 *
 * 今天与明天单独说，是因为用户判断「这条提醒还作不作数」时，最先看的就是这个；
 * 再远的日期直接写月日，不引入星期与「后天」这种多一层换算的说法。
 */
fun formatReminderMoment(
    triggerAtMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val at = Instant.ofEpochMilli(triggerAtMillis).atZone(zone)
    val dayOffset = at.toLocalDate().toEpochDay() -
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
    val time = "%02d:%02d".format(at.hour, at.minute)
    return when (dayOffset) {
        0L -> "今天 $time"
        1L -> "明天 $time"
        else -> at.format(reminderTimeFormatter)
    }
}

/** 把延迟说成人话。
 *
 * 一分钟以内直接说「准时」：秒级偏差用户感知不到，写成「延迟 3 秒」只会让人以为出了问题。
 * 超过一分钟才报具体分钟数，这正是省电策略推迟闹钟时会落到的区间。
 *
 * **现在没有界面在用**：唯一的入口（首页那条常驻提示）按用户口径撤掉了（16.33）。
 * 留着是因为「这一次到底晚了几分钟」的落库口径仍然有效，将来要放回任务弹窗或统计页时，
 * 格式化只有这一处，不要再写第二份。
 */
fun formatReminderLag(lagMillis: Long): String {
    val lag = lagMillis.coerceAtLeast(0)
    val minutes = lag / 60_000
    return when {
        lag < 60_000 -> "准时"
        minutes < 60 -> "延迟 $minutes 分钟"
        else -> "延迟 ${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}

/**
 * 从「哪天提醒 + 当天的分钟数」算出生效时刻。
 *
 * 顺带兜住一个越界值：提醒时间来自用户随手挑的钟点，万一存进来越过 24 点，
 * 硬算会滑到第二天，却仍按前一天的名义显示，等于凭空多出一条错时刻的提醒。
 * 这里直接判为无效（返回 null），界面据此当没设时间处理。
 */
fun reminderTriggerAtMillis(
    epochDay: Long,
    minuteOfDay: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    if (minuteOfDay !in 0 until MINUTES_PER_DAY) return null
    return LocalDate.ofEpochDay(epochDay)
        .atStartOfDay(zone)
        .plusMinutes(minuteOfDay.toLong())
        .toInstant()
        .toEpochMilli()
}

/**
 * 把提醒时刻锚定到「下一个还没到的那个钟点」。
 *
 * 这一层解决的是本应用特有的一个坑：**打卡日以凌晨 6 点换日**，而计划的 `epochDay`
 * 是自然日。于是 00:00–05:59 这段时间里，「今天」的计划还带着前一天的日期：
 * 用户在这个时段给任务设 01:30，字面意思是「半小时后」，但按 epochDay 硬算出来是
 * **前一天**的 01:30，也就是 23 小时之前——这条提醒会被当成过期丢弃，一次都不会响。
 *
 * 规则只有一条，且**只在计划日期正好比当前打卡日晚一天时**才生效：
 * 按计划日期算出来的时刻已经过去，就改用**打卡日当天**的那个钟点。
 * 挪过去的那个钟点仍然可能是过去（比如 02:00 看 01:30 那条），那就照原样交给
 * [pendingReminders] 判定——它落在 grace 窗口里，正是「因为省电晚到几分钟」那条路径依赖的宽度。
 *
 * 不在这条规则里的都保持原样，两条边界都是刻意的：
 * - 计划日期等于打卡日（正常的当天计划）：算出来的时刻本来就没过去，不动；
 *   真已经过去的就让它过去。**不能顺手推到次日**——那会把「今早忘了做的事」变成
 *   「明天早上 8 点还有一条提醒」，与用户排的时间已经不是一回事。
 * - 计划日期比打卡日早两天以上：那确实是陈旧计划，同样保持原样，
 *   由 [pendingReminders] 的 grace 判定丢弃。
 *
 * [checkInEpochDay] 必须传当前的打卡日编号（首页那把凌晨 6 点的尺子）。
 */
fun anchorReminderTriggerAtMillis(
    epochDay: Long,
    minuteOfDay: Int,
    nowMillis: Long,
    checkInEpochDay: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    val candidate = reminderTriggerAtMillis(epochDay, minuteOfDay, zone) ?: return null
    if (candidate >= nowMillis) return candidate
    if (epochDay != checkInEpochDay - 1L) return candidate
    return reminderTriggerAtMillis(checkInEpochDay, minuteOfDay, zone) ?: candidate
}

private const val MINUTES_PER_DAY = 24 * 60

/** 提醒是否已经错过：过了这个点再排就只能靠 grace 补偿，界面上要说清楚。 */
fun isReminderMomentPast(triggerAtMillis: Long, nowMillis: Long): Boolean =
    triggerAtMillis < nowMillis
