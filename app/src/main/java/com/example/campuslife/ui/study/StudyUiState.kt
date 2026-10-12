package com.example.campuslife.ui.study

import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat
import com.example.campuslife.data.study.StudyImportResult

data class StudyUiState(
    val librarySummaryLabel: String,
    val searchQuery: String,
    val documents: List<StudyDocumentUiModel>,
    val totalDocumentCount: Int,
    val selectedCategory: StudyCategory = StudyCategory.Notes,
    val isImporting: Boolean = false,
    val importResult: StudyImportResult? = null,
)

data class StudyDocumentUiModel(
    val id: String,
    val title: String,
    val format: StudyDocumentFormat,
    val sourceName: String,
    val folderLabel: String,
    val detailLabel: String,
    val snapshotId: String,
    val relativePath: String,
    /** 资料内容哈希，用于判断已保存的背单词遮挡状态是否仍然对得上。 */
    val contentHash: String = "",
    val progress: Float = 0f,
    val scrollY: Int = 0,
    val lastReadAtEpochMillis: Long = 0L,
    val category: StudyCategory = StudyCategory.Notes,
    /** 背单词遮挡的已编码状态；资料内容变化后与应用时按过期处理。 */
    val vocabularyMask: String? = null,
    /** 阅读器里被勾选的复选框，见 [com.example.campuslife.data.study.TaskCheckboxState]。 */
    val taskCheckboxState: String? = null,
) {
    val supportsVocabularyTableMask: Boolean
        get() = category == StudyCategory.Vocabulary && format == StudyDocumentFormat.Markdown
}

data class StudyReaderUiState(
    val document: StudyDocumentUiModel,
    val progress: Float,
    val progressLabel: String,
)
