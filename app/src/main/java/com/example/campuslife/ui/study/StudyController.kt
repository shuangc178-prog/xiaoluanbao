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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StudyController(
    val state: StudyUiState,
    val allDocuments: List<StudyDocumentUiModel>,
    val checkedTasksByDocument: Map<String, Set<Int>>,
    val onSelectCategory: (StudyCategory) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onImportFiles: (List<Uri>) -> Unit,
    val onImportFolder: (Uri) -> Unit,
    val onDismissImportResult: () -> Unit,
    val onDeleteDocument: (String) -> Unit,
    val onSetDocumentCategory: (String, StudyCategory) -> Unit,
    val onSaveReading: (id: String, scrollY: Int, progress: Float) -> Unit,
    val onToggleTask: (id: String, index: Int, checked: Boolean) -> Unit,
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
    var checkedTasksByDocument by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }

    LaunchedEffect(store) {
        store.documents.collect { loaded -> documents = loaded }
    }

    // 任务勾选状态由「正文里已勾上的项」和覆盖层合并而来，所以要等资料列表到位后再算一遍；
    // 列表没变时只算一次，避免每次重组都去读 DataStore 和正文文件。
    LaunchedEffect(documents.map { it.id }) {
        if (documents.isEmpty()) {
            checkedTasksByDocument = emptyMap()
            return@LaunchedEffect
        }
        val loaded = documents.associate { document ->
            document.id to runCatching { store.effectiveCheckedTaskIndexes(document) }
                .getOrDefault(emptySet())
        }
        checkedTasksByDocument = loaded
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
        checkedTasksByDocument = checkedTasksByDocument,
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
        onToggleTask = { id, index, checked ->
            // 先照现状改界面，再落库：点击到重绘之间不能有延迟，而落库失败也只影响下次打开。
            checkedTasksByDocument = checkedTasksByDocument + (id to checkedTasksByDocument[id].orEmpty()
                .toMutableSet()
                .apply { if (checked) add(index) else remove(index) })
            documents.firstOrNull { it.id == id }?.let { document ->
                scope.launch {
                    store.setTaskChecked(
                        document = document,
                        index = index,
                        checked = checked,
                        knownStates = store.loadTaskStates(),
                    )
                }
            }
        },
    )
}

private fun toUiModel(document: StudyDocument): StudyDocumentUiModel = StudyDocumentUiModel(
    id = document.id,
    title = document.title,
    format = document.format,
    sourceName = document.sourceName,
    folderLabel = document.folderLabel,
    detailLabel = "${formatFileSize(document.sizeBytes)} · ${formatDate(document.importedAtEpochMillis)}",
    snapshotId = document.snapshotId,
    relativePath = document.relativePath,
    progress = document.progress,
    scrollY = document.scrollY,
    lastReadAtEpochMillis = document.lastReadAtEpochMillis,
    category = document.category,
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
