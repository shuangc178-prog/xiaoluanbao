package com.example.campuslife.data.study

enum class StudyDocumentFormat(
    val shortLabel: String,
) {
    Markdown("MD"),
}

enum class StudyCategory(
    val label: String,
) {
    Vocabulary("背单词"),
    Notes("看笔记"),
    Documents("看文档"),
}

/** App 私有目录中一份只读学习资料快照的索引。 */
data class StudyDocument(
    val id: String,
    val title: String,
    val format: StudyDocumentFormat,
    val sourceName: String,
    val folderLabel: String,
    val snapshotId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val sourceModifiedAtEpochMillis: Long,
    val importedAtEpochMillis: Long,
    val contentHash: String,
    val version: Int = 1,
    val lastReadAtEpochMillis: Long = 0L,
    val scrollY: Int = 0,
    val progress: Float = 0f,
    val category: StudyCategory = StudyCategory.Notes,
    /** 背单词遮挡的已编码状态，见 [VocabularyTableMaskState]；未遮挡或已过期时为空。 */
    val vocabularyMask: String? = null,
    /** 阅读器里被勾选的复选框，见 [TaskCheckboxState]；没有勾选时为空。 */
    val taskCheckboxState: String? = null,
)

data class StudyImportResult(
    val importedCount: Int,
    val replacedCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val errors: List<String> = emptyList(),
    val resourceFolderMessage: String? = null,
) {
    val hasImportedDocuments: Boolean get() = importedCount > 0 || replacedCount > 0
    val requiresResourceFolder: Boolean get() = resourceFolderMessage != null
}
