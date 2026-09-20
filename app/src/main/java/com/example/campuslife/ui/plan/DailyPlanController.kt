package com.example.campuslife.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.DailyPlanStore
import com.example.campuslife.data.plan.PlanTask
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyPlanController(
    /** 今天的计划（已含持久化的勾选状态）。 */
    val today: DailyPlan,
    /** 明天的预排计划；未安排时为空计划。 */
    val tomorrow: DailyPlan,
    /** 历史存档，按日期倒序，[today] 在最前，不包含预排的未来计划。 */
    val history: List<DailyPlan>,
    /** 当前全部有效计划，包含明日预排，供提醒排程使用。 */
    val plansForReminders: List<DailyPlan>,
    /** 今天的任务是否已锁定（当天已结束打卡）。 */
    val isTodayLocked: Boolean,
    val onToggleTask: (String) -> Unit,
    val onAddTask: (title: String, supportingText: String, expectedStartMinute: Int?) -> Unit,
    val onUpdateTask: (
        originalTitle: String,
        title: String,
        supportingText: String,
        expectedStartMinute: Int?,
    ) -> Unit,
    val onDeleteTask: (title: String) -> Unit,
    val onMoveTask: (title: String, targetIndex: Int) -> Unit,
    val onUseAutomaticTaskOrder: () -> Unit,
    val onAddTomorrowTask: (title: String, supportingText: String, expectedStartMinute: Int?) -> Unit,
    val onUpdateTomorrowTask: (
        originalTitle: String,
        title: String,
        supportingText: String,
        expectedStartMinute: Int?,
    ) -> Unit,
    val onDeleteTomorrowTask: (title: String) -> Unit,
    val onMoveTomorrowTask: (title: String, targetIndex: Int) -> Unit,
    val onUseAutomaticTomorrowTaskOrder: () -> Unit,
)

/**
 * 每日计划的存档与勾选。
 *
 * 行为约定：
 * - 当天第一次进入时，用 [seedTasks] 建立当天快照并落盘；之后一律以存档为准。
 * - 勾选状态存在计划里，所以切换 Tab、重启应用都不会丢。
 * - [todayEpochDay] 直接由打卡控制器传入，两者在凌晨 6 点使用同一个状态同时换日。
 * - 明日安排提前写入下一打卡日；换日后它自然成为当天任务栏，不再另做复制。
 *
 * **[isTodayLocked] 为 true 时当天任务整体只读**：打卡一旦完成，勾选就锁住，
 * 不能再取消。这道锁放在这里而不是只放在界面上，是因为当天任务的写入路径全都在这个类里
 * ——只禁用复选框的话，其它入口（未来新增的快捷操作、自动化）依然能把存档改回去，
 * 让「已打卡」与「任务未完成」互相矛盾。**加新任务也要拦**：打卡日的任务集合一旦定下，
 * 之后再插一条未完成的任务，会让这一天的存档看起来并没有全部完成。
 *
 * 锁只覆盖**今天**：历史存档本来就有独立的只读视图；明日预排是另一天，不受影响。
 */
@Composable
fun rememberDailyPlanController(
    seedTasks: List<PlanTask>,
    todayEpochDay: Long,
    isTodayLocked: Boolean = false,
): DailyPlanController {
    val context = LocalContext.current
    val store = remember(context) { DailyPlanStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var plans by remember { mutableStateOf<List<DailyPlan>>(emptyList()) }
    var plansLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        store.plans.collect { loaded ->
            plans = loaded
            plansLoaded = true
        }
    }

    val todayPlan = plans.lastOrNull { it.epochDay == todayEpochDay }

    LaunchedEffect(plansLoaded, todayPlan, todayEpochDay, seedTasks) {
        if (plansLoaded && todayPlan == null && seedTasks.isNotEmpty()) {
            store.savePlan(DailyPlan(epochDay = todayEpochDay, tasks = seedTasks))
        }
    }

    val effectiveToday = todayPlan ?: DailyPlan(epochDay = todayEpochDay, tasks = seedTasks)
    val tomorrowEpochDay = todayEpochDay + 1L
    val effectiveTomorrow = plans.lastOrNull { it.epochDay == tomorrowEpochDay }
        ?: DailyPlan(epochDay = tomorrowEpochDay, tasks = emptyList())
    val effectivePlans = (plans.filterNot { it.epochDay == todayEpochDay } + effectiveToday)
        .sortedBy { it.epochDay }

    return DailyPlanController(
        today = effectiveToday,
        tomorrow = effectiveTomorrow,
        history = effectivePlans
            .filter { it.epochDay <= todayEpochDay }
            .sortedByDescending { it.epochDay },
        plansForReminders = effectivePlans,
        isTodayLocked = isTodayLocked,
        onToggleTask = { title ->
            // 已打卡：勾选锁住，不能再取消。这里拦一道，界面禁用只是为了不骗用户去点。
            if (!isTodayLocked) {
                scope.launch {
                    // 从存储里取最新的那天再翻转：连着点两下时不会拿旧快照互相覆盖。
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(latest.toggle(title))
                }
            }
        },
        onAddTask = { title, supportingText, expectedStartMinute ->
            if (!isTodayLocked) {
                scope.launch {
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(
                        latest.addTask(
                            PlanTask(
                                title = title,
                                supportingText = supportingText,
                                expectedStartMinute = expectedStartMinute,
                            ),
                        ),
                    )
                }
            }
        },
        onUpdateTask = { originalTitle, title, supportingText, expectedStartMinute ->
            if (!isTodayLocked) {
                scope.launch {
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(
                        latest.updateTask(
                            originalTitle = originalTitle,
                            title = title,
                            supportingText = supportingText,
                            expectedStartMinute = expectedStartMinute,
                        ),
                    )
                }
            }
        },
        onDeleteTask = { title ->
            if (!isTodayLocked) {
                scope.launch {
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(latest.removeTask(title))
                }
            }
        },
        onMoveTask = { title, targetIndex ->
            if (!isTodayLocked) {
                scope.launch {
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(latest.moveTask(title, targetIndex))
                }
            }
        },
        onUseAutomaticTaskOrder = {
            if (!isTodayLocked) {
                scope.launch {
                    val latest = store.latestPlan(todayEpochDay, seedTasks)
                    store.savePlan(latest.useAutomaticOrder())
                }
            }
        },
        onAddTomorrowTask = { title, supportingText, expectedStartMinute ->
            scope.launch {
                val latest = store.latestPlan(tomorrowEpochDay, emptyList())
                store.savePlan(
                    latest.addTask(
                        PlanTask(
                            title = title,
                            supportingText = supportingText,
                            expectedStartMinute = expectedStartMinute,
                        ),
                    ),
                )
            }
        },
        onUpdateTomorrowTask = { originalTitle, title, supportingText, expectedStartMinute ->
            scope.launch {
                val latest = store.latestPlan(tomorrowEpochDay, emptyList())
                store.savePlan(
                    latest.updateTask(
                        originalTitle = originalTitle,
                        title = title,
                        supportingText = supportingText,
                        expectedStartMinute = expectedStartMinute,
                    ),
                )
            }
        },
        onDeleteTomorrowTask = { title ->
            scope.launch {
                val latest = store.latestPlan(tomorrowEpochDay, emptyList())
                store.savePlan(latest.removeTask(title))
            }
        },
        onMoveTomorrowTask = { title, targetIndex ->
            scope.launch {
                val latest = store.latestPlan(tomorrowEpochDay, emptyList())
                store.savePlan(latest.moveTask(title, targetIndex))
            }
        },
        onUseAutomaticTomorrowTaskOrder = {
            scope.launch {
                val latest = store.latestPlan(tomorrowEpochDay, emptyList())
                store.savePlan(latest.useAutomaticOrder())
            }
        },
    )
}

private suspend fun DailyPlanStore.latestPlan(
    epochDay: Long,
    seedTasks: List<PlanTask>,
): DailyPlan = plans.first().lastOrNull { it.epochDay == epochDay }
    ?: DailyPlan(epochDay = epochDay, tasks = seedTasks)
