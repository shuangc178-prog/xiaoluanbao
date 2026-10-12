package com.example.campuslife.data.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

private val Context.studyDataStore: DataStore<Preferences> by preferencesDataStore(name = "study")

class StudyStore(private val context: Context) {

    val documents: Flow<List<StudyDocument>> = context.studyDataStore.data.map { preferences ->
        preferences[DOCUMENTS_KEY]
            .orEmpty()
            .mapNotNull(::decodeStudyDocument)
            .sortedByDescending { it.importedAtEpochMillis }
    }

    suspend fun upsertAll(documents: List<StudyDocument>) {
        if (documents.isEmpty()) return
        context.studyDataStore.edit { preferences ->
            val ids = documents.mapTo(mutableSetOf()) { it.id }
            val kept = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .filterNot { encodedStudyDocumentId(it) in ids }
            preferences[DOCUMENTS_KEY] = (kept + documents.map(::encodeStudyDocument)).toSet()
        }
    }

    suspend fun updateReading(
        id: String,
        scrollY: Int,
        progress: Float,
        readAtEpochMillis: Long,
    ) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .map { encoded ->
                    val document = decodeStudyDocument(encoded)
                    if (document?.id == id) {
                        encodeStudyDocument(
                            document.copy(
                                lastReadAtEpochMillis = readAtEpochMillis,
                                scrollY = scrollY.coerceAtLeast(0),
                                progress = progress.coerceIn(0f, 1f),
                            ),
                        )
                    } else {
                        encoded
                    }
                }
                .toSet()
        }
    }

    /** 背单词遮挡状态变化时立即落盘，退出笔记后再次打开仍保持用户翻开的单元格。 */
    suspend fun updateVocabularyMask(id: String, mask: String?) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .map { encoded ->
                    val document = decodeStudyDocument(encoded)
                    if (document?.id == id) {
                        encodeStudyDocument(document.copy(vocabularyMask = mask))
                    } else {
                        encoded
                    }
                }
                .toSet()
        }
    }

    /**
     * 阅读器里勾选复选框后立即落盘。资料的 Markdown 快照保持只读，勾选只存在这里；
     * 资料重新导入后由 [TaskCheckboxState] 逐条比对文字哈希决定哪些勾选还能用。
     */
    suspend fun updateTaskCheckboxState(id: String, state: String?) {
        // 页面状态先走统一编解码器，避免 `|` 等资料索引分隔符混进记录并破坏整份资料。
        val normalizedState = state
            ?.let(::decodeTaskCheckboxState)
            ?.let(::encodeTaskCheckboxState)
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .map { encoded ->
                    val document = decodeStudyDocument(encoded)
                    if (document?.id == id) {
                        encodeStudyDocument(document.copy(taskCheckboxState = normalizedState))
                    } else {
                        encoded
                    }
                }
                .toSet()
        }
    }

    suspend fun updateCategory(id: String, category: StudyCategory) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .map { encoded ->
                    val document = decodeStudyDocument(encoded)
                    if (document?.id == id) {
                        encodeStudyDocument(document.copy(category = category))
                    } else {
                        encoded
                    }
                }
                .toSet()
        }
    }

    suspend fun delete(id: String) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = preferences[DOCUMENTS_KEY]
                .orEmpty()
                .filterNot { encodedStudyDocumentId(it) == id }
                .toSet()
        }
    }

    suspend fun snapshot(): List<StudyDocument> = documents.first()
    suspend fun replaceAll(documents: List<StudyDocument>) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = documents.map(::encodeStudyDocument).toSet()
        }
    }
}

private val DOCUMENTS_KEY = stringSetPreferencesKey("documents")
private const val FIELD_SEPARATOR = "|"
private const val CODEC_VERSION = "4"
private const val TASK_MARK_CODEC_VERSION = "3"
private const val LEGACY_CODEC_VERSION = "2"
private const val OLDEST_CODEC_VERSION = "1"
private const val FIELD_COUNT = 19
private const val TASK_MARK_FIELD_COUNT = 18
private const val LEGACY_FIELD_COUNT = 17
private const val OLDEST_FIELD_COUNT = 16

internal fun encodeStudyDocument(document: StudyDocument): String = listOf(
    CODEC_VERSION,
    document.id,
    document.format.name,
    document.snapshotId,
    document.sizeBytes.toString(),
    document.sourceModifiedAtEpochMillis.toString(),
    document.importedAtEpochMillis.toString(),
    document.version.toString(),
    document.lastReadAtEpochMillis.toString(),
    document.scrollY.toString(),
    document.progress.toString(),
    document.contentHash,
    encodeText(document.title),
    encodeText(document.sourceName),
    encodeText(document.folderLabel),
    encodeText(document.relativePath),
    document.category.name,
    document.vocabularyMask.orEmpty(),
    document.taskCheckboxState.orEmpty(),
).joinToString(FIELD_SEPARATOR)

internal fun decodeStudyDocument(encoded: String): StudyDocument? {
    val parts = encoded.split(FIELD_SEPARATOR)
    val codecVersion = parts.firstOrNull()
    val hasExpectedShape = when (codecVersion) {
        CODEC_VERSION -> parts.size == FIELD_COUNT
        TASK_MARK_CODEC_VERSION -> parts.size == TASK_MARK_FIELD_COUNT
        LEGACY_CODEC_VERSION -> parts.size == LEGACY_FIELD_COUNT
        OLDEST_CODEC_VERSION -> parts.size == OLDEST_FIELD_COUNT
        else -> false
    }
    if (!hasExpectedShape) return null

    return runCatching {
        StudyDocument(
            id = parts[1].takeIf { it.isNotBlank() } ?: return null,
            format = StudyDocumentFormat.valueOf(parts[2]),
            snapshotId = parts[3].takeIf { it.isNotBlank() } ?: return null,
            sizeBytes = parts[4].toLong(),
            sourceModifiedAtEpochMillis = parts[5].toLong(),
            importedAtEpochMillis = parts[6].toLong(),
            version = parts[7].toInt(),
            lastReadAtEpochMillis = parts[8].toLong(),
            scrollY = parts[9].toInt(),
            progress = parts[10].toFloat().coerceIn(0f, 1f),
            contentHash = parts[11].takeIf { it.isNotBlank() } ?: return null,
            title = decodeText(parts[12]),
            sourceName = decodeText(parts[13]),
            folderLabel = decodeText(parts[14]),
            relativePath = decodeText(parts[15]),
            category = if (codecVersion == OLDEST_CODEC_VERSION) {
                StudyCategory.Notes
            } else {
                StudyCategory.valueOf(parts[16])
            },
            vocabularyMask = if (
                codecVersion == CODEC_VERSION || codecVersion == TASK_MARK_CODEC_VERSION
            ) {
                parts[17].takeIf { it.isNotBlank() }
            } else {
                null
            },
            taskCheckboxState = if (codecVersion == CODEC_VERSION) {
                parts[18].takeIf { it.isNotBlank() }
            } else {
                null
            },
        )
    }.getOrNull()
}

private fun encodedStudyDocumentId(encoded: String): String =
    encoded.split(FIELD_SEPARATOR, limit = 3).getOrNull(1).orEmpty()

private fun encodeText(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeText(value: String): String = String(
    Base64.getUrlDecoder().decode(value),
    Charsets.UTF_8,
)
