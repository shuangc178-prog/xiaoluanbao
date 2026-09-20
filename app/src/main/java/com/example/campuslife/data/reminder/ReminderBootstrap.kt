package com.example.campuslife.data.reminder

import android.content.Context
import com.example.campuslife.data.plan.DailyPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 提醒排程的唯一入口。
 *
 * 每次重排都是「先算出应该有哪些闹钟，再把系统里的对齐过去」，而算的依据是存档里的
 * 计划 + 两份簿记。多个入口同时跑就会互相覆盖簿记：一边读到旧的已排闹钟码、
 * 一边刚写完新的，结果取消掉不该取消的那条。所以所有触发点都从这里进，
 * 由 [syncMutex] 串行化。
 *
 * 触发点共五类，缺任何一类都会留下一个「不准时」的缺口：
 *
 * | 触发 | 谁发起 | 缺了会怎样 |
 * |---|---|---|
 * | 计划变化（增删改、勾选） | 界面 | 刚设好的提醒没排上 |
 * | 回到前台 | MainActivity 的 onStart | 被系统清掉的闹钟没人补 |
 * | 开机 / 应用更新 / 改时间 / 换时区 / 授权变化 | ReminderRestoreReceiver | 闹钟整体失效或整体偏移 |
 * | 周期自愈 | ReminderHealWorker | 长期不打开应用时没人复查 |
 * | 状态自检里的「重新排入」 | 界面按钮 | 用户看见了丢失却没法自救 |
 */
object ReminderBootstrap {

    /**
     * Android 13+ 的通知权限名。放在提醒入口上，是为了让界面只需要认识「提醒」这一个模块，
     * 不必到处拼权限字符串。
     */
    const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"

    private val syncMutex = Mutex()

    /** 整个进程共用一个作用域：排程是短任务，不该随界面生死。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var everSynced = false

    /** 是否完成过至少一次重排。界面据此判断「已排入」这类提示有没有依据。 */
    val hasEverSynced: Boolean get() = everSynced

    /**
     * 按存档重排一次，并等待完成。
     *
     * [plans] 传 null 表示**从 DataStore 读**，这正是「回到前台」「周期自愈」要走的路径：
     * 应用被杀掉之后内存里没有计划，但存档里有；反过来，界面已经把计划读出来了，
     * 就没必要再读一遍，直接传进来即可。
     *
     * 两条路径都按存档与系统对齐，所以**不用担心用空数据覆盖掉有效排程**：
     * 真排不出东西时，本来也不该有闹钟留在系统里。
     */
    suspend fun syncNow(context: Context, plans: List<DailyPlan>? = null) {
        val appContext = context.applicationContext
        syncMutex.withLock {
            syncReminders(context = appContext, plans = plans)
            everSynced = true
        }
    }

    /**
     * 把「重排一次」丢到进程级作用域里立刻返回。
     *
     * 给广播接收者和界面回调用：它们都不该为了等一次 DataStore 读写而拖住自己。
     */
    fun syncAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { syncNow(appContext) }
    }

    /**
     * 确保周期自愈任务已经排上。幂等，重复调用不会堆出多个任务。
     *
     * WorkManager 的入队要落盘，所以**不能在主线程上调用**——它自己会抛异常拦住你。
     * 界面在自己的协程里调即可（见 [ensureHealingScheduledAsync]）。
     */
    fun ensureHealingScheduledAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { ReminderHealWorker.ensureScheduled(appContext) }
    }
}
