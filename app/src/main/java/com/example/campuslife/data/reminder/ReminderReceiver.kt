package com.example.campuslife.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campuslife.ui.reminder.ReminderOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟到点后的落点：弹通知，并把这条提醒记成「已经弹过」。
 *
 * 记录必须落地——闹钟响了之后应用随时可能被重新拉起，
 * 没有这条记录，重新排程时会把刚弹过的那条又弹一遍。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: return
        val token = intent.getStringExtra(EXTRA_REMINDER_TOKEN) ?: return
        val body = intent.getStringExtra(EXTRA_REMINDER_BODY).orEmpty()
        val requestCode = intent.getIntExtra(EXTRA_REMINDER_REQUEST_CODE, title.hashCode())
        val scheduledFallback = intent.getLongExtra(
            EXTRA_REMINDER_TRIGGER_AT,
            System.currentTimeMillis(),
        )

        // 先震后弹：振动由应用自己发（见 ReminderVibrator），不等通知系统那一套。
        // 放在通知前面，是因为「震三下」是这条提醒真正要传达的东西，
        // 而通知只是留给用户回头查看的落点。
        ReminderVibrator.buzz(context)

        ReminderNotifier.notify(
            context = context,
            notificationId = requestCode,
            title = title,
            body = body,
        )

        // 浮卡片放在最后：它只在「亮屏且没锁 + 用户给过悬浮窗权限」时才真的出现，
        // 前面两步（震动、通知）才是任何情况下都成立的保底通道。
        ReminderOverlay.show(
            context = context,
            title = title,
            body = body,
            notificationId = requestCode,
        )

        // onReceive 返回后进程可能立刻被回收，所以用 goAsync 把这次写入撑住。
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderStore(context.applicationContext).markDelivered(
                    token = token,
                    // 计划时刻以 token 里的值为准（它才是排程时的那个时刻），
                    // intent 里的时间戳只是解析失败时的兜底。
                    // 与实际弹出时刻一起落库，差值就是这次提醒的真实延迟——
                    // 「到底准不准时」只能靠它对账。
                    scheduledAtMillis = scheduledAtOfToken(token) ?: scheduledFallback,
                    deliveredAtMillis = System.currentTimeMillis(),
                    title = title,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
