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
    val progress: Float = 0f,
    val scrollY: Int = 0,
    val lastReadAtEpochMillis: Long = 0L,
    val category: StudyCategory = StudyCategory.Notes,
) {
    val supportsVocabularyTableMask: Boolean
        get() = category == StudyCategory.Vocabulary && format == StudyDocumentFormat.Markdown
}

data class StudyReaderUiState(
    val document: StudyDocumentUiModel,
    val progress: Float,
    val progressLabel: String,
    /** 任务列表里最终勾上的下标（正文里已勾的项与 App 覆盖层合并后的结果）。 */
    val checkedTaskIndexes: Set<Int> = emptySet(),
)
