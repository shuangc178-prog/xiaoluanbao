package com.example.campuslife.data.journal

/** 用户自行创建的日记分类。 */
data class JournalCategory(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
)

/**
 * 一篇日记只保存一段连续文本：第一行是标题，其余行是正文。
 *
 * 不再分别保存标题、心情和“隐藏预览”开关，编辑器因此可以保持一张完整稿纸的形态。
 */
data class JournalEntry(
    val id: String,
    val content: String,
    val categoryId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val title: String
        get() = content.lineSequence().firstOrNull().orEmpty().trim()

    val body: String
        get() = content.substringAfter('\n', missingDelimiterValue = "").trim()
}

data class JournalData(
    val categories: List<JournalCategory> = emptyList(),
    val entries: List<JournalEntry> = emptyList(),
    val autoSaveEnabled: Boolean = true,
)
