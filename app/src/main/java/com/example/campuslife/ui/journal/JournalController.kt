package com.example.campuslife.ui.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.journal.JournalCategory
import com.example.campuslife.data.journal.JournalData
import com.example.campuslife.data.journal.JournalEntry
import com.example.campuslife.data.journal.JournalStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class JournalController(
    val state: JournalUiState,
    val onSelectCategory: (String) -> Unit,
    val onCreateCategory: (String) -> Unit,
    val onRenameCategory: (id: String, name: String) -> Unit,
    val onDeleteCategory: (String) -> Unit,
    val onSaveEntry: (id: String?, content: String, categoryId: String) -> Unit,
    val onToggleAutoSave: (Boolean) -> Unit,
    val onDeleteEntry: (String) -> Unit,
)

@Composable
fun rememberJournalController(): JournalController {
    val context = LocalContext.current
    val store = remember(context) { JournalStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf(JournalData()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        store.data.collect { loaded -> data = loaded }
    }

    LaunchedEffect(data.categories, selectedCategoryId) {
        if (selectedCategoryId !in data.categories.map { it.id }) {
            selectedCategoryId = data.categories.firstOrNull()?.id
        }
    }

    val uiEntries = data.entries.map(::toUiModel)
    val state = JournalUiState(
        dateLabel = formatHeaderDate(LocalDate.now()),
        entries = uiEntries,
        categories = data.categories.map { category ->
            JournalCategoryUiModel(
                id = category.id,
                name = category.name,
                count = uiEntries.count { it.categoryId == category.id },
            )
        },
        selectedCategoryId = selectedCategoryId,
        autoSaveEnabled = data.autoSaveEnabled,
    )

    return JournalController(
        state = state,
        onSelectCategory = { categoryId ->
            if (categoryId in data.categories.map { it.id }) selectedCategoryId = categoryId
        },
        onCreateCategory = { name ->
            val normalizedName = name.trim()
            if (
                normalizedName.isNotEmpty() &&
                data.categories.none { it.name.equals(normalizedName, ignoreCase = true) }
            ) {
                val category = JournalCategory(
                    id = UUID.randomUUID().toString(),
                    name = normalizedName,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
                scope.launch {
                    store.upsertCategory(category)
                    selectedCategoryId = category.id
                }
            }
        },
        onRenameCategory = { id, name ->
            val normalizedName = name.trim()
            val category = data.categories.firstOrNull { it.id == id }
            if (
                category != null &&
                normalizedName.isNotEmpty() &&
                data.categories.none {
                    it.id != id && it.name.equals(normalizedName, ignoreCase = true)
                }
            ) {
                scope.launch { store.upsertCategory(category.copy(name = normalizedName)) }
            }
        },
        onDeleteCategory = { id ->
            if (data.categories.any { it.id == id }) {
                if (selectedCategoryId == id) selectedCategoryId = null
                scope.launch { store.deleteCategory(id) }
            }
        },
        onSaveEntry = { id, content, categoryId ->
            val normalizedContent = content.trim()
            if (
                normalizedContent.isNotEmpty() &&
                data.categories.any { it.id == categoryId }
            ) {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val existing = id?.let { entryId ->
                        store.entries.first().firstOrNull { it.id == entryId }
                    }
                    store.upsert(
                        JournalEntry(
                            // 编辑器对新笔记传进来的是提前定好的草稿 id：
                            // 自动保存反复落库时覆盖同一条记录，而不是每次新建。
                            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
                            content = normalizedContent,
                            categoryId = existing?.categoryId ?: categoryId,
                            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                }
            }
        },
        onToggleAutoSave = { enabled ->
            scope.launch { store.setAutoSaveEnabled(enabled) }
        },
        onDeleteEntry = { id ->
            scope.launch { store.delete(id) }
        },
    )
}

private fun toUiModel(entry: JournalEntry): JournalEntryUiModel {
    val bodyPreview = entry.body
        .replace(Regex("\\s+"), " ")
        .trim()
    return JournalEntryUiModel(
        id = entry.id,
        content = entry.content,
        title = entry.title,
        snippet = bodyPreview,
        dateLabel = formatEntryDate(entry.updatedAtEpochMillis),
        categoryId = entry.categoryId,
    )
}

private val headerDateFormatter = DateTimeFormatter.ofPattern(
    "M月d日 · EEEE",
    Locale.SIMPLIFIED_CHINESE,
)

private fun formatHeaderDate(date: LocalDate): String = date.format(headerDateFormatter)

private fun formatEntryDate(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
}
