package com.example.campuslife.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campuslife.data.checkin.checkInEpochDay
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.DailyPlanStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 重新对齐系统里的闹钟，让实际排程等于「按当前计划存档算出来应该有的排程」。
 *
 * 幂等：随时可以重复调用。会取消已经不该存在的闹钟（任务改了时间或勾完），并补上缺的那些。
 *
 * **不要直接调用它**——并发调用会互相覆盖「已排闹钟码」这份簿记。统一走
 * `ReminderBootstrap`，由它串行化。
 *
 * [plans] 允许由调用方传入，避免界面已经把数据读出来之后这里再读一遍；
 * 传 null 表示从 DataStore 读（应用被杀掉后重新拉起时就是这条路径）。
 *
 * 打卡日**没有**做成参数，而是与 [nowMillis] 一起现算：两者必须是同一把尺子，
 * 分开传迟早会出现「NOW 用测试值、打卡日用真实时间」这种错一天的组合。
 */
internal suspend fun syncReminders(
    context: Context,
    plans: List<DailyPlan>? = null,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val appContext = context.applicationContext
    val store = ReminderStore(appContext)
    val effectivePlans = plans ?: DailyPlanStore(appContext).plans.first()
    val checkInEpochDay = Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .checkInEpochDay()

    val desired = pendingReminders(
        candidates = buildReminderCandidates(
            plans = effectivePlans,
            checkInEpochDay = checkInEpochDay,
            nowMillis = nowMillis,
        ),
        deliveredTokens = store.deliveredTokens(),
        nowMillis = nowMillis,
    )

    val desiredCodes = desired.mapTo(mutableSetOf()) { it.requestCode }
    store.scheduledRequestCodes()
        .filterNot { it in desiredCodes }
        .forEach { ReminderAlarms.cancel(appContext, it) }
    desired.forEach { ReminderAlarms.schedule(appContext, it) }
    store.saveScheduledRequestCodes(desiredCodes)
}

/**
 * 系统侧那几件会让排程失效或失准的事发生后，按存档重排一遍闹钟。
 *
 * 闹钟存在 system_server 的 `AlarmManager` 里，进程不在了也还在，所以大部分时候不用管；
 * 但下面这几件事会让它整体失效或整体偏移，必须重建：
 *
 * - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`：重启与应用更新会**清空全部闹钟**。
 * - `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`：用户授权精确闹钟之后，
 *   要把之前用非精确闹钟排下的那批换成精确闹钟，否则会一直慢下去。
 *   （本项目走 `USE_EXACT_ALARM`，安装即授予，正常不会走到这条；
 *   留着是为了兼容将来改用授权引导的情形。）
 * - `TIME_SET` / `TIMEZONE_CHANGED`：闹钟用的是 `RTC_WAKEUP`，触发时刻是**墙钟绝对毫秒**。
 *   用户改了系统时间或换了时区之后，原来那条「早上 8:30」对应的毫秒数就指向了别的钟点，
 *   不重算就会在错的时刻响。
 *
 * 接收者必须在 Manifest 里注册：应用被杀掉时进程内注册的收不到这些广播。
 */
class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }

        // onReceive 返回后进程可能立刻被回收，所以用 goAsync 把这次重排撑住；
        // 这几条广播都发生在应用很可能不在前台的时刻（开机、改系统时间），
        // 光把任务丢给进程作用域、直接返回，写入有可能被中途掐断。
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderBootstrap.syncNow(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
