package com.example.campuslife.data.plan

import com.example.campuslife.data.checkin.CHECK_IN_DAY_START_HOUR
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class TaskLearningPhase { Idle, Running, Paused, Finished }

val PlanTask.learningPhase: TaskLearningPhase
    get() = when {
        isCompleted -> TaskLearningPhase.Finished
        learningStartedAtEpochMillis != null -> TaskLearningPhase.Running
        hasLearningStarted || learningElapsedMillis > 0L -> TaskLearningPhase.Paused
        else -> TaskLearningPhase.Idle
    }

/** 同一逻辑日最多计到次日 06:00；过日后继续读取也不会多算。 */
fun learningDayEndMillis(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDate.ofEpochDay(epochDay).plusDays(1)
        .atTime(CHECK_IN_DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

fun PlanTask.learningDurationMillis(
    epochDay: Long,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val started = learningStartedAtEpochMillis?.takeUnless { isCompleted }
    val extra = started?.let {
        (minOf(nowEpochMillis, learningDayEndMillis(epochDay, zone)) - it).coerceAtLeast(0L)
    } ?: 0L
    val settled = learningElapsedMillis.coerceAtLeast(0L)
    return settled + extra.coerceAtMost(Long.MAX_VALUE - settled)
}

fun PlanTask.pauseLearning(epochDay: Long, nowEpochMillis: Long): PlanTask = copy(
    learningElapsedMillis = learningDurationMillis(epochDay, nowEpochMillis),
    learningStartedAtEpochMillis = null,
    hasLearningStarted = hasLearningStarted || learningStartedAtEpochMillis != null,
)

fun DailyPlan.pauseLearning(nowEpochMillis: Long): DailyPlan = copy(
    tasks = tasks.map { it.pauseLearning(epochDay, nowEpochMillis) },
)

/** 一次原子写入内暂停其它任务，再开始目标任务；完成的任务保持只读。 */
fun List<DailyPlan>.toggleTaskLearning(
    epochDay: Long,
    title: String,
    nowEpochMillis: Long,
): List<DailyPlan> {
    val target = firstOrNull { it.epochDay == epochDay }?.tasks?.firstOrNull { it.title == title }
        ?: return this
    if (target.isCompleted || nowEpochMillis >= learningDayEndMillis(epochDay)) return this
    val wasRunning = target.learningStartedAtEpochMillis != null
    return map { plan ->
        plan.copy(tasks = plan.tasks.map { task ->
            val paused = task.pauseLearning(plan.epochDay, nowEpochMillis)
            if (plan.epochDay == epochDay && task.title == title && !wasRunning) {
                paused.copy(learningStartedAtEpochMillis = nowEpochMillis, hasLearningStarted = true)
            } else paused
        })
    }
}

fun DailyPlan.finishTaskLearning(title: String, nowEpochMillis: Long): DailyPlan = copy(
    tasks = tasks.map { task ->
        if (task.title == title && !task.isCompleted) {
            task.pauseLearning(epochDay, nowEpochMillis).copy(isCompleted = true)
        } else task
    },
)

/** 切页、后台无需逐秒写库；仅在换日时把过期的活动段结算一次。 */
fun List<DailyPlan>.settleExpiredLearning(nowEpochMillis: Long): List<DailyPlan> = map { plan ->
    if (nowEpochMillis >= learningDayEndMillis(plan.epochDay)) plan.pauseLearning(nowEpochMillis)
    else plan
}

fun formatLearningDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = seconds / 60L % 60L
    val remainder = seconds % 60L
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, remainder)
}
