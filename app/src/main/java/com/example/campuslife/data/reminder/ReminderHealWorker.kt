package com.example.campuslife.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 周期自愈：定期回头查一遍排程，缺的补上、多的取消。
 *
 * **它解决的是什么，以及不解决什么，必须分清**（否则很容易以为加了它就万无一失）：
 *
 * - 解决的问题：应用长期不被打开时，系统可能因为省电策略把闹钟丢掉或改期，
 *   过程中应用收不到任何通知。周期任务会在下一个窗口里把它补回来。
 * - **不解决的问题：force-stop**。应用被系统强制停止后，`AlarmManager` 里的闹钟会被
 *   清空，**WorkManager 的周期任务同样被阻塞**，要等用户下一次手动打开应用才会恢复。
 *   这是平台层硬规则，任何 API 都救不回来；只能靠系统的自启动 / 省电白名单避免被 force-stop。
 *
 * WorkManager 在这里比前台服务合适：Android 12 起禁止从后台（含广播接收者）启动前台服务，
 * 闹钟到点时应用在后台会被 `ForegroundServiceStartNotAllowedException` 挡掉；
 * 而周期任务没有这个限制，代价只是触发窗口会被 Doze 推迟——自愈本来就是慢动作，
 * 推迟几小时可以接受，真正要求准时的那条链路始终是 `setAlarmClock`。
 */
class ReminderHealWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 失败也不要 retry 到天荒地老：下一次周期窗口本来就会再查一遍。
        return try {
            ReminderBootstrap.syncNow(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            Result.success()
        }
    }

    companion object {

        /** 固定名字 + KEEP：重复调用不会堆出多个周期任务。 */
        private const val UNIQUE_WORK_NAME = "reminder-heal"

        /**
         * 6 小时一次。选这个间隔的理由：
         * - 用户的提醒最多落在「今天」与「明天」，6 小时足够在一天之内复查好几轮；
         * - 再密也只是被 Doze 拦在窗口外面，白耗电；
         * - 这是**保底**，不是主路径——主路径是排程当下的 `setAlarmClock`，
         *   所以这里不需要更激进。
         */
        private const val INTERVAL_HOURS = 6L

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderHealWorker>(
                INTERVAL_HOURS,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
