package com.example.campuslife.ui.today

import com.example.campuslife.data.checkin.formatMinuteOfDay

data class TodayUiState(
    val timeLabel: String = "13:15",
    val dateLabel: String = "9月14日 · 星期一",
    val priorities: List<TodayPriorityUiModel>,
)

data class TodayPriorityUiModel(
    val id: String,
    val title: String,
    val supportingText: String,
    val category: TodayCategory,
    val status: TodayItemStatus = TodayItemStatus.NotStarted,
    /** 预期开始时间（当天 0 点起的分钟数），未安排时为 null。 */
    val expectedStartMinute: Int? = null,
    /** null 为时间自动排序；非 null 为用户手动指定的位置。 */
    val manualOrder: Int? = null,
) {
    val expectedStartLabel: String? get() = expectedStartMinute?.let(::formatMinuteOfDay)
}

/**
 * 按一天内的预期开始时间从早到晚排列。
 *
 * 没安排时间的任务放在所有已排时任务之后；同一时刻和未排时任务都保留原有顺序，
 * 避免用户只编辑一条任务时其它任务也发生无意义的位置变化。
 */
internal fun List<TodayPriorityUiModel>.sortedByExpectedStartTime(): List<TodayPriorityUiModel> =
    withIndex()
        .sortedWith(
            compareBy<IndexedValue<TodayPriorityUiModel>>(
                { it.value.expectedStartMinute ?: Int.MAX_VALUE },
                { it.index },
            ),
        )
        .map { it.value }

internal val List<TodayPriorityUiModel>.isManuallyOrdered: Boolean
    get() = isNotEmpty() && all { it.manualOrder != null }

/** 手动位置完整时优先使用；否则维持默认的时间自动排序。 */
internal fun List<TodayPriorityUiModel>.sortedForTaskBar(): List<TodayPriorityUiModel> =
    if (isManuallyOrdered) {
        withIndex()
            .sortedWith(compareBy({ it.value.manualOrder ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    } else {
        sortedByExpectedStartTime()
    }

enum class TodayCategory {
    Study,
    Vocabulary,
    Fitness,
    Personal,
}

/**
 * 计划事项的状态。
 *
 * 原型阶段刻意不设「部分完成」：勾选是唯一的完成操作，
 * 状态再多一档就要解释「部分完成到什么程度」，收益不成比例。
 */
enum class TodayItemStatus {
    NotStarted,
    InProgress,
    Completed,
}
