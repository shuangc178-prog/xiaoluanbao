package com.example.campuslife.ui.today

object TodayPreviewData {
    val default = TodayUiState(
        timeLabel = "13:15",
        dateLabel = "9月14日 · 星期一",
        priorities = listOf(
            TodayPriorityUiModel(
                id = "math-mistakes",
                title = "整理高数第三章错题",
                supportingText = "高等数学 · 40 分钟",
                category = TodayCategory.Study,
                expectedStartMinute = 9 * 60 + 30,
            ),
            TodayPriorityUiModel(
                id = "cet4-review",
                title = "四级单词到期复习",
                supportingText = "28 个待复习 · 约 15 分钟",
                category = TodayCategory.Vocabulary,
                expectedStartMinute = 13 * 60,
            ),
            TodayPriorityUiModel(
                id = "upper-body",
                title = "上肢力量训练",
                supportingText = "学校健身房 · 35 分钟",
                category = TodayCategory.Fitness,
                expectedStartMinute = 19 * 60,
            ),
        ),
    )

    val empty = TodayUiState(
        timeLabel = "13:15",
        dateLabel = "9月14日 · 星期一",
        priorities = emptyList(),
    )

    val populated = default.copy(
        priorities = listOf(
            default.priorities[0].copy(status = TodayItemStatus.InProgress),
            default.priorities[1].copy(status = TodayItemStatus.Completed),
            default.priorities[2].copy(status = TodayItemStatus.InProgress),
            TodayPriorityUiModel(
                id = "hidden-fourth-item",
                title = "阅读社团资料",
                supportingText = "社团 · 20 分钟",
                category = TodayCategory.Personal,
            ),
        ),
    )

    /** 已完成的任务标题，供 Preview 直接喂给 TodayScreen。 */
    val populatedCompletedTitles: Set<String> = completedTitlesOf(populated)

    private fun completedTitlesOf(state: TodayUiState): Set<String> = state.priorities
        .filter { it.status == TodayItemStatus.Completed }
        .map { it.title }
        .toSet()
}
