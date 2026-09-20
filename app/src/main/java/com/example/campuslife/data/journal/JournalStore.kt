package com.example.campuslife.data.journal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

private val Context.journalDataStore: DataStore<Preferences> by preferencesDataStore(name = "journal")

/** 本地保存用户分类与日记；读取时兼容旧版固定分类、心情和锁定字段。 */
class JournalStore(private val context: Context) {

    val data: Flow<JournalData> = context.journalDataStore.data.map(::decodeJournalData)

    val entries: Flow<List<JournalEntry>> = data.map { it.entries }

    suspend fun upsertCategory(category: JournalCategory) {
        context.journalDataStore.edit { preferences ->
            val kept = preferences[CATEGORIES_KEY]
                .orEmpty()
                .filterNot { encodedCategoryId(it) == category.id }
            preferences[CATEGORIES_KEY] = (kept + encodeJournalCategory(category)).toSet()
        }
    }

    /** 删除分类时一并删除其下的日记；界面会在执行前明确二次确认。 */
    suspend fun deleteCategory(id: String) {
        context.journalDataStore.edit { preferences ->
            preferences[CATEGORIES_KEY] = preferences[CATEGORIES_KEY]
                .orEmpty()
                .filterNot { encodedCategoryId(it) == id }
                .toSet()
            preferences[ENTRIES_KEY] = preferences[ENTRIES_KEY]
                .orEmpty()
                .filterNot { decodeJournalEntry(it)?.categoryId == id }
                .toSet()
        }
    }

    suspend fun upsert(entry: JournalEntry) {
        context.journalDataStore.edit { preferences ->
            val kept = preferences[ENTRIES_KEY]
                .orEmpty()
                .filterNot { encodedJournalId(it) == entry.id }
            preferences[ENTRIES_KEY] = (kept + encodeJournalEntry(entry)).toSet()
        }
    }

    suspend fun delete(id: String) {
        context.journalDataStore.edit { preferences ->
            preferences[ENTRIES_KEY] = preferences[ENTRIES_KEY]
                .orEmpty()
                .filterNot { encodedJournalId(it) == id }
                .toSet()
        }
    }

    /** 编辑器自动保存开关；默认打开，用户可以随时关掉。 */
    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        context.journalDataStore.edit { preferences ->
            preferences[AUTO_SAVE_KEY] = enabled
        }
    }

    suspend fun snapshot(): JournalData = data.first()

    suspend fun replaceAll(data: JournalData) {
        context.journalDataStore.edit { preferences ->
            preferences[CATEGORIES_KEY] = data.categories.map(::encodeJournalCategory).toSet()
            preferences[ENTRIES_KEY] = data.entries.map(::encodeJournalEntry).toSet()
            preferences[AUTO_SAVE_KEY] = data.autoSaveEnabled
        }
    }
}

private val ENTRIES_KEY = stringSetPreferencesKey("entries")
private val CATEGORIES_KEY = stringSetPreferencesKey("categories")
private val AUTO_SAVE_KEY = booleanPreferencesKey("auto_save_enabled")
private const val FIELD_SEPARATOR = "|"
private const val ENTRY_VERSION = "2"
private const val ENTRY_V2_FIELD_COUNT = 6
private const val LEGACY_ENTRY_FIELD_COUNT = 8
private const val CATEGORY_FIELD_COUNT = 3
private const val LEGACY_CATEGORY_PREFIX = "legacy-"

private val legacyCategoryLabels = linkedMapOf(
    "Study" to "学习",
    "Life" to "生活",
    "Mood" to "情绪",
    "Fitness" to "运动",
    "Reading" to "读书",
    "Other" to "其他",
)

internal fun encodeJournalEntry(entry: JournalEntry): String = listOf(
    ENTRY_VERSION,
    entry.id,
    entry.createdAtEpochMillis.toString(),
    entry.updatedAtEpochMillis.toString(),
    encodeText(entry.categoryId),
    encodeText(entry.content),
).joinToString(FIELD_SEPARATOR)

internal fun decodeJournalEntry(encoded: String): JournalEntry? {
    val parts = encoded.split(FIELD_SEPARATOR)
    return when {
        parts.size == ENTRY_V2_FIELD_COUNT && parts.first() == ENTRY_VERSION -> {
            runCatching {
                JournalEntry(
                    id = parts[1].takeIf { it.isNotBlank() } ?: return null,
                    createdAtEpochMillis = parts[2].toLong(),
                    updatedAtEpochMillis = parts[3].toLong(),
                    categoryId = decodeText(parts[4]).takeIf { it.isNotBlank() } ?: return null,
                    content = decodeText(parts[5]),
                )
            }.getOrNull()
        }

        parts.size == LEGACY_ENTRY_FIELD_COUNT -> decodeLegacyJournalEntry(parts)
        else -> null
    }
}

internal fun encodeJournalCategory(category: JournalCategory): String = listOf(
    category.id,
    category.createdAtEpochMillis.toString(),
    encodeText(category.name),
).joinToString(FIELD_SEPARATOR)

internal fun decodeJournalCategory(encoded: String): JournalCategory? {
    val parts = encoded.split(FIELD_SEPARATOR)
    if (parts.size != CATEGORY_FIELD_COUNT) return null

    return runCatching {
        JournalCategory(
            id = parts[0].takeIf { it.isNotBlank() } ?: return null,
            createdAtEpochMillis = parts[1].toLong(),
            name = decodeText(parts[2]).takeIf { it.isNotBlank() } ?: return null,
        )
    }.getOrNull()
}

private fun decodeJournalData(preferences: Preferences): JournalData {
    val entries = preferences[ENTRIES_KEY]
        .orEmpty()
        .mapNotNull(::decodeJournalEntry)
        .sortedByDescending { it.updatedAtEpochMillis }
    val storedCategories = preferences[CATEGORIES_KEY]
        .orEmpty()
        .mapNotNull(::decodeJournalCategory)
    val autoSaveEnabled = preferences[AUTO_SAVE_KEY] ?: true

    // 旧版本没有单独保存分类；为旧日记补出可重命名、可删除的用户分类。
    val storedIds = storedCategories.mapTo(mutableSetOf()) { it.id }
    val migratedCategories = entries
        .map { it.categoryId }
        .distinct()
        .filterNot { it in storedIds }
        .mapIndexed { index, id ->
            JournalCategory(
                id = id,
                name = legacyCategoryName(id) ?: "未分类",
                createdAtEpochMillis = Long.MIN_VALUE + index,
            )
        }

    return JournalData(
        categories = (storedCategories + migratedCategories)
            .sortedWith(compareBy<JournalCategory> { it.createdAtEpochMillis }.thenBy { it.id }),
        entries = entries,
        autoSaveEnabled = autoSaveEnabled,
    )
}

private fun decodeLegacyJournalEntry(parts: List<String>): JournalEntry? = runCatching {
    val legacyCategory = parts[3]
    if (legacyCategory !in legacyCategoryLabels) return null
    val title = decodeText(parts[6]).trim()
    val body = decodeText(parts[7]).trim()
    val content = when {
        title.isNotEmpty() && body.isNotEmpty() -> "$title\n$body"
        title.isNotEmpty() -> title
        else -> body
    }

    JournalEntry(
        id = parts[0].takeIf { it.isNotBlank() } ?: return null,
        createdAtEpochMillis = parts[1].toLong(),
        updatedAtEpochMillis = parts[2].toLong(),
        categoryId = legacyCategoryId(legacyCategory),
        content = content,
    )
}.getOrNull()

private fun legacyCategoryId(legacyName: String): String =
    "$LEGACY_CATEGORY_PREFIX${legacyName.lowercase()}"

private fun legacyCategoryName(id: String): String? {
    val legacyName = id.removePrefix(LEGACY_CATEGORY_PREFIX)
    if (legacyName == id) return null
    return legacyCategoryLabels.entries.firstOrNull {
        it.key.equals(legacyName, ignoreCase = true)
    }?.value
}

private fun encodedJournalId(encoded: String): String {
    val parts = encoded.split(FIELD_SEPARATOR, limit = ENTRY_V2_FIELD_COUNT)
    return if (parts.size == ENTRY_V2_FIELD_COUNT && parts.first() == ENTRY_VERSION) {
        parts[1]
    } else {
        parts.firstOrNull().orEmpty()
    }
}

private fun encodedCategoryId(encoded: String): String = encoded.substringBefore(FIELD_SEPARATOR)

private fun encodeText(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeText(value: String): String = String(
    Base64.getUrlDecoder().decode(value),
    Charsets.UTF_8,
)
