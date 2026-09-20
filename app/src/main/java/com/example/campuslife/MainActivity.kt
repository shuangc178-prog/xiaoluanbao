package com.example.campuslife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.campuslife.data.reminder.ReminderBootstrap
import com.example.campuslife.data.reminder.ReminderNotifier
import com.example.campuslife.data.backup.CloudBackupScheduler
import com.example.campuslife.ui.navigation.CampusLifeApp
import com.example.campuslife.ui.splash.LaunchSplash
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.theme.rememberThemeController

class MainActivity : ComponentActivity() {

    /**
     * 每次回到前台都重排一次闹钟。
     *
     * 这是「被系统悄悄清掉的闹钟」最主要的恢复路径：系统清掉闹钟时不发任何通知，
     * 用户也无从知道，只能靠应用自己每次回到前台就回头查一遍、缺的补上。
     *
     * 放在 `onStart` 而不是 `onCreate`：从最近任务切回来、锁屏解锁后回来都会再次触发，
     * 而 `onCreate` 一生只跑一次。
     */
    private val foregroundResync = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            ReminderBootstrap.syncAsync(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 通知渠道要在第一条提醒弹出来之前就建好，否则系统会按默认重要性建，横幅弹不出来。
        ReminderNotifier.ensureChannel(this)
        // 周期自愈任务排一次即可，WorkManager 自己记住它，重启后仍有效。
        ReminderBootstrap.ensureHealingScheduledAsync(this)
        // 首次在下一次 6 点运行；若系统延迟或设备当时离线，联网后由 WorkManager 补做。
        CloudBackupScheduler.ensureScheduledAsync(this)
        // 打开应用也是一条补偿路径：当天还没成功备份时立即排一次，成功记录会挡住重复上传。
        CloudBackupScheduler.enqueueCatchUp(this)
        lifecycle.addObserver(foregroundResync)
        setContent {
            // 主题状态比 CampusLifeTheme 更靠外，这里持有后再把切换入口交给页面。
            val theme = rememberThemeController()
            CampusLifeTheme(darkTheme = theme.isDarkTheme) {
                var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
                if (showLaunchSplash) {
                    LaunchSplash(onFinished = { showLaunchSplash = false })
                } else {
                    CampusLifeApp(
                        isDarkTheme = theme.isDarkTheme,
                        onToggleTheme = theme.onToggle,
                    )
                }
            }
        }
    }
}
