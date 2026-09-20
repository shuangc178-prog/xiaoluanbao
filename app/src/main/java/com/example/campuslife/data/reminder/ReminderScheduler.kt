package com.example.campuslife.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.campuslife.MainActivity

internal const val EXTRA_REMINDER_REQUEST_CODE = "extra_reminder_request_code"
internal const val EXTRA_REMINDER_KEY = "extra_reminder_key"
internal const val EXTRA_REMINDER_TOKEN = "extra_reminder_token"
internal const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
internal const val EXTRA_REMINDER_BODY = "extra_reminder_body"
internal const val EXTRA_REMINDER_TRIGGER_AT = "extra_reminder_trigger_at"

private const val ACTION_FIRE_REMINDER = "com.example.campuslife.action.FIRE_REMINDER"

/** 闹钟图标点击后的跳转 intent 用的固定码，避开提醒哈希可能取到的值。 */
private const val SHOW_INTENT_REQUEST_CODE = 0x5A1A0001

/**
 * 把待触发提醒交给系统闹钟。
 *
 * 用 AlarmManager 而不是应用内的定时器：进程被杀掉之后应用内的计时器一并消失，
 * 而闹钟归系统管，应用不在了照样会响。
 */
internal object ReminderAlarms {

    /**
     * 排一条提醒，返回是否用上了 [AlarmManager.setAlarmClock]（用户可见闹钟）。
     *
     * 返回值只是给自检那层做参考：真正的判据是回头问系统的 [ReminderSystemStateReader]，
     * `getNextAlarmClock()` 只会看见用户可见闹钟，所以这里返回 false 就意味着
     * 排程已经退化到会被批处理的那几档，界面上该提示用户了。
     *
     * 优先级从高到低：
     * 1. **`setAlarmClock`（用户可见闹钟）**。系统把它当「用户自己设的闹钟」对待，
     *    不受低电耗批处理的推迟，也不会被后台省电策略随意丢；状态栏会显示一个闹钟图标。
     *    这是「应用在后台甚至被划掉也要响」唯一靠得住的一档。
     * 2. `setExactAndAllowWhileIdle`：精确，但受空闲配额限制（每应用每小时有额度），
     *    额度用完后会被推迟。
     * 3. `setAndAllowWhileIdle`：**官方明说这个调用总是允许批处理**，
     *    低电耗空闲下间隔「可能长达 15 分钟」——这就是「提醒不准」最常见的来源，
     *    只作为最后兜底。
     *
     * 每一档都可能因为缺少精确闹钟授权抛 `SecurityException`，所以逐级往下退。
     */
    fun schedule(context: Context, reminder: ScheduledReminder): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val operation = pendingIntent(context, reminder.requestCode, reminder)

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    reminder.triggerAtMillis,
                    showIntent(context),
                ),
                operation,
            )
            return true
        } catch (_: SecurityException) {
            // 没有精确闹钟授权：继续往下退。
        }

        try {
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    operation,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
        }
        return false
    }

    fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(context, requestCode, reminder = null)
        alarmManager.cancel(operation)
        operation.cancel()
    }

    /** Android 12 起精确闹钟要单独授权；没给就只能排非精确闹钟。 */
    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * 闹钟图标被点开时打开的页面。所有提醒共用一个即可——它只影响系统 UI 的跳转。
     * requestCode 取一个远离哈希区间的常量，避免和某条提醒的码撞上。
     */
    private fun showIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            SHOW_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * PendingIntent 的相等性只看 action / data / type / component / requestCode，不看 extras；
     * 所以取消时即便不带 extras，拿到的仍是同一个 PendingIntent。
     */
    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        reminder: ScheduledReminder?,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_REMINDER_REQUEST_CODE, requestCode)
            if (reminder != null) {
                putExtra(EXTRA_REMINDER_KEY, reminder.key)
                putExtra(EXTRA_REMINDER_TOKEN, reminder.token)
                putExtra(EXTRA_REMINDER_TITLE, reminder.title)
                putExtra(EXTRA_REMINDER_BODY, reminder.body)
                putExtra(EXTRA_REMINDER_TRIGGER_AT, reminder.triggerAtMillis)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
