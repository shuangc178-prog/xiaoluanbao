package com.example.campuslife.data.plan

import com.example.campuslife.data.checkin.formatMinuteOfDay
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 计划里的一条任务。
 *
 * 跨天匹配用 [title]，因此任务改名会被当成一条新任务（历史完成状态不跟着走）。
 * 用标题而不是下标匹配，是为了让任务顺序调整后仍能对上。
 *
 * [expectedStartMinute] 是「预期开始时间」——当天 0 点起的分钟数，可选。
 * 按产品文档 8.2 的口径，它属于**计划执行时间**：用户可以随时挪动或取消，
 * 到点只弹一次提醒，不会把任务标记成任何状态。
 */
data class PlanTask(
    val title: String,
    val supportingText: String,
    val isCompleted: Boolean = false,
    val expectedStartMinute: Int? = null,
    /** null 表示跟随时间自动排序；非 null 表示用户选择的手动位置（从 0 开始）。 */
    val manualOrder: Int? = null,
    /** 已结算的实际学习时间；暂停时不再增加。 */
    val learningElapsedMillis: Long = 0L,
    /** 正在计时的一段的开始时刻，跨进程重启保留。 */
    val learningStartedAtEpochMillis: Long? = null,
    val hasLearningStarted: Boolean = false,
) {
    /** `8 * 60 + 30` → `08:30`；没安排时间时为 null。 */
    val expectedStartLabel: String? get() = expectedStartMinute?.let(::formatMinuteOfDay)
}

/** 某一天的完整计划快照。 */
data class DailyPlan(
    val epochDay: Long,
    val tasks: List<PlanTask>,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val completedCount: Int get() = tasks.count { it.isCompleted }
    val totalCount: Int get() = tasks.size
    val isAllCompleted: Boolean get() = tasks.isNotEmpty() && tasks.all { it.isCompleted }
    val isManuallyOrdered: Boolean get() = tasks.isNotEmpty() && tasks.all { it.manualOrder != null }

    /** 页面实际展示顺序：手动位置完整时按位置，否则按预期开始时间自动排列。 */
    val orderedTasks: List<PlanTask>
        get() = tasks.withIndex()
            .sortedWith(
                if (isManuallyOrdered) {
                    compareBy<IndexedValue<PlanTask>>(
                        { it.value.manualOrder ?: Int.MAX_VALUE },
                        { it.index },
                    )
                } else {
                    compareBy<IndexedValue<PlanTask>>(
                        { it.value.expectedStartMinute ?: Int.MAX_VALUE },
                        { it.index },
                    )
                },
            )
            .map { it.value }

    fun toggle(title: String): DailyPlan = copy(
        tasks = tasks.map { task ->
            if (task.title == title) task.copy(isCompleted = !task.isCompleted) else task
        },
    )

    /** 标题是当前原型里的任务标识，因此同一天不允许出现两个同名任务。 */
    fun addTask(task: PlanTask): DailyPlan {
        val normalized = task.normalized() ?: return this
        if (tasks.any { it.title == normalized.title }) return this
        val taskToAdd = if (isManuallyOrdered) {
            normalized.copy(manualOrder = tasks.size)
        } else {
            normalized.copy(manualOrder = null)
        }
        return copy(tasks = tasks + taskToAdd)
    }

    /**
     * 编辑任务时用旧标题定位，保存后历史当天的任务直接更新为新内容。
     * 如果新标题为空或与另一条任务重复，则保持原计划不变。
     *
     * [expectedStartMinute] 没有默认值：清空时间与「没填」在语义上不同，
     * 让调用方显式传，避免以后多一个入口时不小心把用户安排好的时间抹掉。
     */
    fun updateTask(
        originalTitle: String,
        title: String,
        supportingText: String,
        expectedStartMinute: Int?,
    ): DailyPlan {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return this
        if (tasks.any { it.title != originalTitle && it.title == normalizedTitle }) return this

        return copy(
            tasks = tasks.map { task ->
                if (task.title == originalTitle) {
                    task.copy(
                        title = normalizedTitle,
                        supportingText = supportingText.trim(),
                        expectedStartMinute = expectedStartMinute,
                    )
                } else {
                    task
                }
            },
        )
    }

    fun removeTask(title: String): DailyPlan {
        val remaining = tasks.filterNot { it.title == title }
        return copy(
            tasks = if (isManuallyOrdered) {
                remaining.sortedBy { it.manualOrder }.mapIndexed { index, task ->
                    task.copy(manualOrder = index)
                }
            } else {
                remaining
            },
        )
    }

    /** 把指定任务移动到目标位置，并将当天计划切换为持久化的手动排序。 */
    fun moveTask(title: String, targetIndex: Int): DailyPlan {
        val current = orderedTasks
        val sourceIndex = current.indexOfFirst { it.title == title }
        if (sourceIndex < 0 || current.isEmpty()) return this

        val destination = targetIndex.coerceIn(0, current.lastIndex)
        val reordered = current.toMutableList().apply {
            val selected = removeAt(sourceIndex)
            add(destination, selected)
        }
        return copy(
            tasks = reordered.mapIndexed { index, task -> task.copy(manualOrder = index) },
        )
    }

    /** 清除手动位置；下一次展示重新按预期开始时间自动排序。 */
    fun useAutomaticOrder(): DailyPlan = copy(
        tasks = tasks.map { it.copy(manualOrder = null) },
    )
}

private fun PlanTask.normalized(): PlanTask? {
    val normalizedTitle = title.trim()
    if (normalizedTitle.isEmpty()) return null
    return copy(
        title = normalizedTitle,
        supportingText = supportingText.trim(),
    )
}

/** `9月14日 · 周一`。 */
fun formatPlanDateLabel(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日 · ${weekdayLabel(date.dayOfWeek)}"

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/**
 * 存储编码：`日期;序号;是否完成;标题;补充说明;预期开始分钟;手动位置`，
 * 用 ASCII 的字段分隔符（U+001F）。
 *
 * 不用逗号、竖线这类常见字符，是因为标题由用户输入、可能包含它们；
 * 用一个正文里几乎不可能出现的控制字符，就不需要再写转义逻辑。
 *
 * 新字段始终追加在末尾：5/6/7 段的旧记录仍可读取，缺少时间、排序与计时字段时，
 * 分别按未排时间、自动排序、学习时间为 0 处理。旧完成状态保留。
 */
private const val FIELD_SEPARATOR = "\u001F"
private const val LEGACY_FIELD_COUNT = 5
private const val TIME_FIELD_COUNT = 6
private const val ORDER_FIELD_COUNT = 7
private const val FIELD_COUNT = 10

internal data class StoredPlanTask(
    val epochDay: Long,
    val index: Int,
    val task: PlanTask,
)

internal fun encodePlanTask(epochDay: Long, index: Int, task: PlanTask): String = listOf(
    epochDay.toString(),
    index.toString(),
    if (task.isCompleted) "1" else "0",
    task.title,
    task.supportingText,
    task.expectedStartMinute?.toString().orEmpty(),
    task.manualOrder?.toString().orEmpty(),
    task.learningElapsedMillis.toString(),
    task.learningStartedAtEpochMillis?.toString().orEmpty(),
    if (task.hasLearningStarted) "1" else "0",
).joinToString(FIELD_SEPARATOR)

internal fun decodePlanTask(encoded: String): StoredPlanTask? {
    val parts = encoded.split(FIELD_SEPARATOR)
    if (parts.size !in setOf(LEGACY_FIELD_COUNT, TIME_FIELD_COUNT, ORDER_FIELD_COUNT, FIELD_COUNT)) return null
    val epochDay = parts[0].toLongOrNull() ?: return null
    val index = parts[1].toIntOrNull() ?: return null
    return StoredPlanTask(
        epochDay = epochDay,
        index = index,
        task = PlanTask(
            title = parts[3],
            supportingText = parts[4],
            isCompleted = parts[2] == "1",
            // 老格式没有第 6 段，读作「没安排时间」；新格式里空串同理。
            expectedStartMinute = parts.getOrNull(5)?.toIntOrNull(),
            // 手动位置是最后追加的第 7 段；旧记录默认继续按时间自动排序。
            manualOrder = parts.getOrNull(6)?.toIntOrNull()?.takeIf { it >= 0 },
            learningElapsedMillis = parts.getOrNull(7)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            learningStartedAtEpochMillis = parts.getOrNull(8)?.toLongOrNull()
                ?.takeIf { it >= 0L && parts[2] != "1" },
            hasLearningStarted = parts.getOrNull(9) == "1",
        ),
    )
}

internal fun encodedEpochDay(encoded: String): Long? =
    encoded.substringBefore(FIELD_SEPARATOR).toLongOrNull()

internal fun groupPlans(stored: List<StoredPlanTask>): List<DailyPlan> = stored
    .groupBy { it.epochDay }
    .map { (epochDay, tasks) ->
        DailyPlan(epochDay = epochDay, tasks = tasks.sortedBy { it.index }.map { it.task })
    }
    .sortedBy { it.epochDay }
