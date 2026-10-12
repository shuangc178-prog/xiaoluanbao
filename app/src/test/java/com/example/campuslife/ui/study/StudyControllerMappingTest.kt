package com.example.campuslife.ui.study

import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocument
import com.example.campuslife.data.study.StudyDocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyControllerMappingTest {

    @Test
    fun readerModelKeepsTheContentHashNeededToRestoreVocabularyMasks() {
        val stored = StudyDocument(
            id = "words",
            title = "Words",
            format = StudyDocumentFormat.Markdown,
            sourceName = "words.md",
            folderLabel = "English",
            snapshotId = "snapshot",
            relativePath = "words.md",
            sizeBytes = 128L,
            sourceModifiedAtEpochMillis = 1L,
            importedAtEpochMillis = 2L,
            contentHash = "content-hash-123",
            category = StudyCategory.Vocabulary,
            vocabularyMask = "0:1:content-hash-123:2",
        )

        val readerModel = toUiModel(stored)

        assertEquals(stored.contentHash, readerModel.contentHash)
        assertEquals(stored.vocabularyMask, readerModel.vocabularyMask)
    }

    @Test
    fun readerModelDropsMasksWrittenWithoutAUsableContentHash() {
        val stored = StudyDocument(
            id = "words",
            title = "Words",
            format = StudyDocumentFormat.Markdown,
            sourceName = "words.md",
            folderLabel = "English",
            snapshotId = "snapshot",
            relativePath = "words.md",
            sizeBytes = 128L,
            sourceModifiedAtEpochMillis = 1L,
            importedAtEpochMillis = 2L,
            contentHash = "current-hash",
            category = StudyCategory.Vocabulary,
            vocabularyMask = "0:1::2",
        )

        assertNull(toUiModel(stored).vocabularyMask)
        assertNull(toUiModel(stored.copy(vocabularyMask = "0:1:old-hash:2")).vocabularyMask)
    }

    @Test
    fun readerModelKeepsOnlyTaskStateForTheCurrentDocumentContent() {
        val contentHash = "abcdef1234567890"
        val checked = "v1\nabcdef123456\n0;1;0;9f3a1c229f3a"
        val allUnchecked = "v1\nabcdef123456"
        val stored = StudyDocument(
            id = "tasks",
            title = "Tasks",
            format = StudyDocumentFormat.Markdown,
            sourceName = "tasks.md",
            folderLabel = "Notes",
            snapshotId = "snapshot",
            relativePath = "tasks.md",
            sizeBytes = 128L,
            sourceModifiedAtEpochMillis = 1L,
            importedAtEpochMillis = 2L,
            contentHash = contentHash,
            taskCheckboxState = checked,
        )

        assertEquals(checked, toUiModel(stored).taskCheckboxState)
        assertEquals(allUnchecked, toUiModel(stored.copy(taskCheckboxState = allUnchecked)).taskCheckboxState)
        assertNull(
            toUiModel(stored.copy(taskCheckboxState = "v1\ndeadbeef1234\n0;1;0;9f3a1c229f3a"))
                .taskCheckboxState,
        )
    }
}
