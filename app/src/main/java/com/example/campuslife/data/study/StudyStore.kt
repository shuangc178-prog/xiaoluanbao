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
import java.io.File
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

    /** 任务勾选覆盖层：按资料 id 索引，正文里的 `[x]` 不在这里重复记录。 */
    suspend fun loadTaskStates(): Map<String, StudyTaskState> = context.studyDataStore.data
        .map { preferences -> preferences[TASK_STATES_KEY].orEmpty().mapNotNull(::decodeStudyTaskState) }
        .first()
        .associateBy { it.documentId }

    /** 整包备份用：覆盖层原始记录，顺序固定便于往返比对。 */
    suspend fun taskStateSnapshot(): List<StudyTaskState> = context.studyDataStore.data
        .map { preferences ->
            preferences[TASK_STATES_KEY].orEmpty()
                .mapNotNull(::decodeStudyTaskState)
                .sortedBy { it.documentId }
        }
        .first()

    suspend fun replaceTaskStates(states: List<StudyTaskState>) {
        context.studyDataStore.edit { preferences ->
            preferences[TASK_STATES_KEY] = states.map(::encodeStudyTaskState).toSet()
        }
    }

    /**
     * 任务列表的最终勾选状态 = 正文里已勾上的项 ⊕ 覆盖层里翻转过的项。
     *
     * 这里按 [document] 当前的指纹过滤一次覆盖层：重新导入会换掉内容哈希，旧勾选整条作废，
     * 不会错位到新正文的行上。
     */
    suspend fun effectiveCheckedTaskIndexes(document: StudyDocument): Set<Int> {
        val stored = taskStateSnapshot()
            .firstOrNull { it.documentId == document.id }
            ?.takeIf { it.sourceFingerprint == studyTaskStateFingerprint(document) }
            ?.checkedIndexes
            .orEmpty()
        val source = runCatching {
            File(File(File(context.filesDir, IMPORTS_DIRECTORY_NAME), document.snapshotId), document.relativePath)
                .readText(Charsets.UTF_8)
        }.getOrDefault("")
        return effectiveCheckedTaskIndexes(source, stored)
    }

    suspend fun setTaskChecked(
        document: StudyDocument,
        index: Int,
        checked: Boolean,
        knownStates: Map<String, StudyTaskState>,
    ) {
        if (index < 0) return
        val fingerprint = studyTaskStateFingerprint(document)
        val stored = knownStates[document.id]
            ?.takeIf { it.sourceFingerprint == fingerprint }
            ?: StudyTaskState(documentId = document.id, sourceFingerprint = fingerprint)
        val next = stored.toggled(index, checked)
        context.studyDataStore.edit { preferences ->
            preferences[TASK_STATES_KEY] = preferences[TASK_STATES_KEY]
                .orEmpty()
                .filterNot { decodeStudyTaskState(it)?.documentId == document.id }
                .toSet()
                .plus(encodeStudyTaskState(next))
        }
    }

    suspend fun replaceAll(documents: List<StudyDocument>) {
        context.studyDataStore.edit { preferences ->
            preferences[DOCUMENTS_KEY] = documents.map(::encodeStudyDocument).toSet()
        }
    }
}

private val DOCUMENTS_KEY = stringSetPreferencesKey("documents")
private val TASK_STATES_KEY = stringSetPreferencesKey("task_states")
private const val IMPORTS_DIRECTORY_NAME = "study_imports"
private const val FIELD_SEPARATOR = "|"
private const val CODEC_VERSION = "2"
private const val LEGACY_CODEC_VERSION = "1"
private const val FIELD_COUNT = 17
private const val LEGACY_FIELD_COUNT = 16

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
).joinToString(FIELD_SEPARATOR)

internal fun decodeStudyDocument(encoded: String): StudyDocument? {
    val parts = encoded.split(FIELD_SEPARATOR)
    val codecVersion = parts.firstOrNull()
    val hasExpectedShape = when (codecVersion) {
        CODEC_VERSION -> parts.size == FIELD_COUNT
        LEGACY_CODEC_VERSION -> parts.size == LEGACY_FIELD_COUNT
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
            category = if (codecVersion == CODEC_VERSION) {
                StudyCategory.valueOf(parts[16])
            } else {
                StudyCategory.Notes
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
