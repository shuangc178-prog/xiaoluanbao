package com.example.campuslife.ui.study

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocument
import com.example.campuslife.data.study.StudyImporter
import com.example.campuslife.data.study.StudyStore
import com.example.campuslife.data.study.decodeTaskCheckboxState
import com.example.campuslife.data.study.decodeVocabularyTableMask
import com.example.campuslife.data.study.encodeTaskCheckboxState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StudyController(
    val state: StudyUiState,
    val allDocuments: List<StudyDocumentUiModel>,
    val onSelectCategory: (StudyCategory) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onImportFiles: (List<Uri>) -> Unit,
    val onImportFolder: (Uri) -> Unit,
    val onDismissImportResult: () -> Unit,
    val onDeleteDocument: (String) -> Unit,
    val onSetDocumentCategory: (String, StudyCategory) -> Unit,
    val onSaveReading: (id: String, scrollY: Int, progress: Float) -> Unit,
    val onSaveVocabularyMask: (id: String, mask: String?) -> Unit,
    val onSaveTaskCheckboxState: (id: String, state: String?) -> Unit,
)

@Composable
fun rememberStudyController(): StudyController {
    val context = LocalContext.current
    val store = remember(context) { StudyStore(context.applicationContext) }
    val importer = remember(context, store) { StudyImporter(context.applicationContext, store) }
    val scope = rememberCoroutineScope()

    var documents by remember { mutableStateOf<List<StudyDocument>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(StudyCategory.Notes) }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<com.example.campuslife.data.study.StudyImportResult?>(null) }

    LaunchedEffect(store) {
        store.documents.collect { loaded -> documents = loaded }
    }

    val allUiDocuments = documents.map(::toUiModel)
    val categoryDocuments = allUiDocuments.filter { it.category == selectedCategory }
    val normalizedQuery = searchQuery.trim()
    val visibleDocuments = categoryDocuments.filter { document ->
        normalizedQuery.isEmpty() || listOf(
            document.title,
            document.folderLabel,
            document.sourceName,
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }

    val state = StudyUiState(
        librarySummaryLabel = if (categoryDocuments.isEmpty()) {
            "此分类还没有资料"
        } else {
            "${categoryDocuments.size} 份资料 · 仅保存在本机"
        },
        searchQuery = searchQuery,
        documents = visibleDocuments,
        totalDocumentCount = categoryDocuments.size,
        selectedCategory = selectedCategory,
        isImporting = isImporting,
        importResult = importResult,
    )

    fun import(block: suspend StudyImporter.() -> com.example.campuslife.data.study.StudyImportResult) {
        if (isImporting) return
        isImporting = true
        importResult = null
        scope.launch {
            importResult = runCatching { importer.block() }
                .getOrElse { error ->
                    com.example.campuslife.data.study.StudyImportResult(
                        importedCount = 0,
                        errors = listOf(error.message ?: "导入失败"),
                    )
                }
            isImporting = false
        }
    }

    return StudyController(
        state = state,
        allDocuments = allUiDocuments,
        onSelectCategory = { selectedCategory = it },
        onSearchQueryChange = { searchQuery = it },
        onImportFiles = { uris ->
            val destination = selectedCategory
            import { importFiles(uris, destination) }
        },
        onImportFolder = { uri ->
            val destination = selectedCategory
            import { importFolder(uri, destination) }
        },
        onDismissImportResult = { importResult = null },
        onDeleteDocument = { id ->
            documents.firstOrNull { it.id == id }?.let { document ->
                scope.launch { importer.deleteDocument(document) }
            }
        },
        onSetDocumentCategory = { id, category ->
            scope.launch { store.updateCategory(id, category) }
        },
        onSaveReading = { id, scrollY, progress ->
            scope.launch {
                store.updateReading(
                    id = id,
                    scrollY = scrollY,
                    progress = progress,
                    readAtEpochMillis = System.currentTimeMillis(),
                )
            }
        },
        onSaveVocabularyMask = { id, mask ->
            // 先更新内存中的资料，用户点完单元格立刻返回再重进时也能拿到最新状态；
            // DataStore 的流随后会用同一份持久化结果收敛这里的数据。
            documents = documents.map { document ->
                if (document.id == id) document.copy(vocabularyMask = mask) else document
            }
            scope.launch { store.updateVocabularyMask(id, mask) }
        },
        onSaveTaskCheckboxState = { id, state ->
            val normalizedState = state
                ?.let(::decodeTaskCheckboxState)
                ?.let(::encodeTaskCheckboxState)
            // 连续点选后立即退出、重进，也先从内存拿到最后一次勾选结果。
            documents = documents.map { document ->
                if (document.id == id) {
                    document.copy(taskCheckboxState = normalizedState)
                } else {
                    document
                }
            }
            scope.launch { store.updateTaskCheckboxState(id, normalizedState) }
        },
    )
}

internal fun toUiModel(document: StudyDocument): StudyDocumentUiModel = StudyDocumentUiModel(
    id = document.id,
    title = document.title,
    format = document.format,
    sourceName = document.sourceName,
    folderLabel = document.folderLabel,
    detailLabel = "${formatFileSize(document.sizeBytes)} · ${formatDate(document.importedAtEpochMillis)}",
    snapshotId = document.snapshotId,
    relativePath = document.relativePath,
    contentHash = document.contentHash,
    progress = document.progress,
    scrollY = document.scrollY,
    lastReadAtEpochMillis = document.lastReadAtEpochMillis,
    category = document.category,
    // 早期有问题的版本曾写入空哈希；资料正文变化后也可能留下旧坐标。
    // 两类状态都不能自动遮挡，等用户重新选列时再由新状态覆盖。
    vocabularyMask = document.vocabularyMask?.takeIf { encoded ->
        decodeVocabularyTableMask(encoded)?.matchesSource(document.contentHash) == true
    },
    taskCheckboxState = document.taskCheckboxState
        ?.let(::decodeTaskCheckboxState)
        ?.takeIf { it.matchesSource(document.contentHash) }
        ?.let(::encodeTaskCheckboxState),
)

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)

private fun formatDate(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    return when (dateTime.toLocalDate()) {
        today -> "今天 ${dateTime.format(timeFormatter)}"
        today.minusDays(1) -> "昨天"
        else -> dateTime.format(dateFormatter)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
