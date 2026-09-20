package com.example.campuslife.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.campuslife.MainActivity
import com.example.campuslife.R

/**
 * 提醒通知的渠道与样式。
 *
 * 交互约定（用户明确要求）：
 * - **只在到点时弹一次**，不重复提醒、不自动取消挂起；
 * - 弹出后**留在通知栏，直到用户手动划掉**，所以 `setAutoCancel(false)` + `setOngoing(false)`：
 *   前者保证点开通知不会顺手把它清掉，后者保证用户仍然能划掉它。
 *   （不用 `setOngoing(true)`——那会让用户根本关不掉，与「手动关闭」是两件事。）
 * - **振动不在这里**：三下短震由 [ReminderVibrator] 在 [ReminderReceiver] 里直接发出，
 *   渠道与通知都**不再振动**。理由是用户实测「装了没震」——通知振动那条链路上有
 *   MIUI 的「振动」开关、勿扰与静音、不重要通知折叠这些应用看不见的静默开关；
 *   振动是这条提醒唯一不靠看的通道，不能托付给它们。两个振动源同时开着还可能叠成六下。
 *
 * 刻意**不做**的事，都是试过之后撤掉的（产品文档 16.31）：不做全屏意图、不做整屏闹钟页、
 * 不响铃。提醒的形态就是「三下短震 + 通知栏一条」，不抢屏、不打断手上的事。
 */
object ReminderNotifier {

    /**
     * 渠道 id 带版本号是**必须的**，不是洁癖：渠道的振动模式建好之后**应用改不了**
     * （系统允许用户改，不允许应用改）。这一版把振动整个从渠道上拿掉、改由应用自己震，
     * 已经装在机器上的旧渠道不会跟着变，只能换新 id 重建。
     */
    const val CHANNEL_ID = "campus_life_reminders_v3"

    /**
     * 历史渠道，一次启动全部删掉。
     *
     * v1：默认「嗡一下」，没有自定义模式。
     * v2：渠道自己震三下——那一版的问题正是「渠道振动可能被系统静默吃掉」。
     * 留着它们只会在系统设置里和当前这条同名并列，让人不知道该信哪一条。
     */
    private val LEGACY_CHANNEL_IDS = listOf(
        "campus_life_reminders",
        "campus_life_reminders_v2",
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "日程提醒",
                // HIGH 才会在到点时以横幅形式从顶部弹出，而不是安静地躺在通知栏里。
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "任务预期开始时间。到点会震三下并留一条通知，需要手动划掉。"
                // 显式关掉：振动由 ReminderVibrator 负责，渠道再震一次就成了六下。
                enableVibration(false)
            },
        )
    }

    fun notify(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
    ) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // 通知栏小图标用应用自己的像素角色剪影（drawable-nodpi 下不随密度缩放）。
            // 注意：Android 只取小图标的 alpha 通道并按系统主题重新染色，
            // 所以这里必须是一张单色剪影图，彩色图会被压成一块实心色。
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)
            // 刻意**不加** `setOnlyAlertOnce(true)`：它会让「同一个 id 再来一条」变成静默更新——
            // 用户没划掉上一条时，下一条既不震也不弹横幅，正好是这个功能最该避免的失败方式。
            // 「同一条提醒只弹一次」由 token 簿记（ReminderStore）保证，不靠这条标志。
            //
            // 也刻意**不写** `setVibrate`：振动统一由 [ReminderVibrator] 发，
            // 这里再写一份，某些 ROM 上会和它叠起来震成六下。
            .setContentIntent(openAppIntent(context, notificationId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // 用户在系统里关掉了通知权限：提醒功能静默失效，不在应用内再抛异常。
        }
    }

    /** 点通知本体进应用：看的是当前计划，不需要这条提醒的内容，所以不带 extras。 */
    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
