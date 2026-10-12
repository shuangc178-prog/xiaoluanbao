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
import com.example.campuslife.data.plan.toggleTaskLearning
import com.example.campuslife.data.plan.finishTaskLearning
import com.example.campuslife.data.plan.settleExpiredLearning
import com.example.campuslife.data.plan.learningDayEndMillis
import com.example.campuslife.data.checkin.checkInEpochDay
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.flow.collect
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
    val onToggleTaskTimer: (String) -> Unit,
    val onFinishTask: (String) -> Unit,
    val nowEpochMillis: Long,
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

/** 每次操作在同一个 DataStore 事务内读取最新计划并更新，计时与排序不会互相覆盖。 */
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
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(store) { store.plans.collect { plans = it } }
    val hasRunningTimer = plans.any { plan -> plan.tasks.any { it.learningStartedAtEpochMillis != null } }
    LaunchedEffect(hasRunningTimer) {
        do {
            nowEpochMillis = System.currentTimeMillis()
            if (!hasRunningTimer) break
            delay(1_000L)
        } while (true)
    }
    LaunchedEffect(plans, todayEpochDay) {
        val now = System.currentTimeMillis()
        if (plans.any { plan ->
                now >= learningDayEndMillis(plan.epochDay) &&
                    plan.tasks.any { it.learningStartedAtEpochMillis != null }
            }) {
            store.updatePlans { it.settleExpiredLearning(now) }
        }
    }

    val today = plans.firstOrNull { it.epochDay == todayEpochDay } ?: DailyPlan(todayEpochDay, seedTasks)
    val tomorrowDay = todayEpochDay + 1L
    val tomorrow = plans.firstOrNull { it.epochDay == tomorrowDay } ?: DailyPlan(tomorrowDay, emptyList())
    val effectivePlans = (plans.filterNot { it.epochDay == todayEpochDay } + today).sortedBy { it.epochDay }

    fun changePlan(day: Long, action: (DailyPlan, Long) -> DailyPlan) {
        if (day == todayEpochDay && isTodayLocked) return
        scope.launch {
            store.updatePlans { latest ->
                // 过 6 点后旧页面发来的点击不能再写回昨天或把明日任务写错一天。
                if (ZonedDateTime.now().checkInEpochDay() != todayEpochDay) latest
                else {
                    val now = System.currentTimeMillis()
                    val current = latest.firstOrNull { it.epochDay == day }
                        ?: DailyPlan(day, if (day == todayEpochDay) seedTasks else emptyList())
                    (latest.filterNot { it.epochDay == day } + action(current, now)).sortedBy { it.epochDay }
                }
            }
            nowEpochMillis = System.currentTimeMillis()
        }
    }

    return DailyPlanController(
        today = today,
        tomorrow = tomorrow,
        history = effectivePlans.filter { it.epochDay <= todayEpochDay }.sortedByDescending { it.epochDay },
        plansForReminders = effectivePlans,
        isTodayLocked = isTodayLocked,
        nowEpochMillis = nowEpochMillis,
        onToggleTaskTimer = { title ->
            if (!isTodayLocked) scope.launch {
                store.updatePlans { latest ->
                    if (ZonedDateTime.now().checkInEpochDay() != todayEpochDay) latest
                    else latest.toggleTaskLearning(todayEpochDay, title, System.currentTimeMillis())
                }
                nowEpochMillis = System.currentTimeMillis()
            }
        },
        onFinishTask = { title -> changePlan(todayEpochDay) { plan, now -> plan.finishTaskLearning(title, now) } },
        onAddTask = { title, supporting, minute ->
            changePlan(todayEpochDay) { plan, _ -> plan.addTask(PlanTask(title, supporting, expectedStartMinute = minute)) }
        },
        onUpdateTask = { original, title, supporting, minute ->
            changePlan(todayEpochDay) { plan, _ -> plan.updateTask(original, title, supporting, minute) }
        },
        onDeleteTask = { title -> changePlan(todayEpochDay) { plan, _ -> plan.removeTask(title) } },
        onMoveTask = { title, index -> changePlan(todayEpochDay) { plan, _ -> plan.moveTask(title, index) } },
        onUseAutomaticTaskOrder = { changePlan(todayEpochDay) { plan, _ -> plan.useAutomaticOrder() } },
        onAddTomorrowTask = { title, supporting, minute ->
            changePlan(tomorrowDay) { plan, _ -> plan.addTask(PlanTask(title, supporting, expectedStartMinute = minute)) }
        },
        onUpdateTomorrowTask = { original, title, supporting, minute ->
            changePlan(tomorrowDay) { plan, _ -> plan.updateTask(original, title, supporting, minute) }
        },
        onDeleteTomorrowTask = { title -> changePlan(tomorrowDay) { plan, _ -> plan.removeTask(title) } },
        onMoveTomorrowTask = { title, index -> changePlan(tomorrowDay) { plan, _ -> plan.moveTask(title, index) } },
        onUseAutomaticTomorrowTaskOrder = { changePlan(tomorrowDay) { plan, _ -> plan.useAutomaticOrder() } },
    )
}
