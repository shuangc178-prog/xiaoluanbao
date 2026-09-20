package com.example.campuslife.ui.journal

data class JournalUiState(
    val dateLabel: String,
    val entries: List<JournalEntryUiModel>,
    val categories: List<JournalCategoryUiModel>,
    val selectedCategoryId: String? = null,
    /** 编辑器自动保存开关，落库持久化，用户可随时关掉。 */
    val autoSaveEnabled: Boolean = true,
) {
    val selectedCategory: JournalCategoryUiModel?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    val visibleEntries: List<JournalEntryUiModel>
        get() = selectedCategoryId?.let { categoryId ->
            entries.filter { it.categoryId == categoryId }
        }.orEmpty()
}

data class JournalEntryUiModel(
    val id: String,
    val content: String,
    val title: String,
    val snippet: String,
    val dateLabel: String,
    val categoryId: String,
)

data class JournalCategoryUiModel(
    val id: String,
    val name: String,
    val count: Int,
)
