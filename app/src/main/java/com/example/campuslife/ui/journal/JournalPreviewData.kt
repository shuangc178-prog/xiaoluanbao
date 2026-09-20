package com.example.campuslife.ui.journal

object JournalPreviewData {

    private const val STUDY_ID = "study-notes"
    private const val LIFE_ID = "daily-life"
    private const val READING_ID = "reading-notes"

    private val entries = listOf(
        JournalEntryUiModel(
            id = "j1",
            content = "终于听懂了\n物理课的麦克斯韦方程组终于串起来了，晚上又整理了一遍笔记。",
            title = "终于听懂了",
            snippet = "物理课的麦克斯韦方程组终于串起来了，晚上又整理了一遍笔记。",
            dateLabel = "今天",
            categoryId = STUDY_ID,
        ),
        JournalEntryUiModel(
            id = "j2",
            content = "复习有点乱\n四级单词背到一半就走神了，明天试试早上先背。",
            title = "复习有点乱",
            snippet = "四级单词背到一半就走神了，明天试试早上先背。",
            dateLabel = "9月13日",
            categoryId = STUDY_ID,
        ),
        JournalEntryUiModel(
            id = "j3",
            content = "夜跑三公里\n操场人不多，风挺凉，跑完洗个澡睡得特别早。",
            title = "夜跑三公里",
            snippet = "操场人不多，风挺凉，跑完洗个澡睡得特别早。",
            dateLabel = "9月12日",
            categoryId = LIFE_ID,
        ),
        JournalEntryUiModel(
            id = "j4",
            content = "读了两章\n《人类简史》比想象中好读，记了几页笔记。",
            title = "读了两章",
            snippet = "《人类简史》比想象中好读，记了几页笔记。",
            dateLabel = "9月10日",
            categoryId = READING_ID,
        ),
    )

    private val categories = listOf(
        JournalCategoryUiModel(id = STUDY_ID, name = "学习复盘", count = 2),
        JournalCategoryUiModel(id = LIFE_ID, name = "日常", count = 1),
        JournalCategoryUiModel(id = READING_ID, name = "读书笔记", count = 1),
    )

    val default = JournalUiState(
        dateLabel = "9月14日 · 星期一",
        entries = entries,
        categories = categories,
        selectedCategoryId = STUDY_ID,
    )

    val empty = JournalUiState(
        dateLabel = "9月14日 · 星期一",
        entries = emptyList(),
        categories = emptyList(),
    )
}
