package com.example.campuslife.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 回头问系统：本应用的提醒排程现在到底还在不在、准不准。
 *
 * 这一层存在的唯一理由是**系统清掉闹钟是静默的**——应用收不到任何通知。
 * 所以只能主动查，把「丢了」变成界面上看得见的一行。
 *
 * 判据是 `AlarmManager.getNextAlarmClock()`，它**只看得见 `setAlarmClock` 排的
 * 用户可见闹钟**。这带来一个必须写清楚的局限：退化成非精确闹钟时它什么都查不到，
 * 那时显示的「查不到」并不等于闹钟丢了，而是这一档本来就不可见。
 * 所以界面要先看权限档位、再看查得到查不到，顺序不能反。
 */
object ReminderStatusReader {

    /** 读一份当前状态快照；纯读操作，不修改任何东西。 */
    suspend fun read(context: Context): ReminderStatus {
        val appContext = context.applicationContext
        val system = readSystemState(appContext)
        val lastDelivery = ReminderStore(appContext).lastDelivery()
        return buildReminderStatus(
            system = system,
            ownPackageName = appContext.packageName,
            lastDelivery = lastDelivery,
        )
    }

    private fun readSystemState(context: Context): ReminderSystemState {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nextAlarm = alarmManager?.nextAlarmClock
        return ReminderSystemState(
            capability = exactAlarmCapability(alarmManager),
            notificationPermissionGranted = notificationPermissionGranted(context),
            batteryOptimizationIgnored = batteryOptimizationIgnored(context),
            systemNextAlarmAtMillis = nextAlarm?.triggerTime,
            // 这个包名是**排这条闹钟的应用**，不是我们的。用它区分「我们排的」和
            // 「系统时钟排的」——状态栏上的闹钟图标可能来自任何一个应用，
            // 不判断归属就会把别人的闹钟当成本应用的排程还在，报出假的安心。
            systemNextAlarmCreatorPackage = nextAlarm?.showIntent?.creatorPackage,
            overlayAllowed = overlayAllowed(context),
        )
    }

    /**
     * 用户给没给悬浮窗权限。
     *
     * 读它的理由与其它几项一样：**没给时系统什么都不说**——提醒照样震、照样进通知栏，
     * 只有「浮在别的应用上面」这一条静默失效。用户会以为功能坏了，所以要在界面上说出来，
     * 并给一个能直接走到那一页的入口。
     */
    private fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    private fun exactAlarmCapability(alarmManager: AlarmManager?): ExactAlarmCapability = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> ExactAlarmCapability.NotRequired
        alarmManager == null -> ExactAlarmCapability.Missing
        alarmManager.canScheduleExactAlarms() -> ExactAlarmCapability.Granted
        else -> ExactAlarmCapability.Missing
    }

    /**
     * Android 13 起通知是运行时权限。用 [NotificationManagerCompat.areNotificationsEnabled]
     * 而不是只查权限：用户在系统里关掉整个应用的通知时，权限可能还留着，
     * 而那时通知一样弹不出来。
     */
    private fun notificationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 没有电池优化豁免时，后台被拦、划掉应用后提醒不会响。这一项只读不改。 */
    private fun batteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
