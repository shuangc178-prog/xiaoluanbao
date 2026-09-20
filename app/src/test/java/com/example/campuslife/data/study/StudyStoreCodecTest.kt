package com.example.campuslife.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyStoreCodecTest {

    @Test
    fun encodeDecodeRoundTripPreservesDocumentMetadata() {
        val document = StudyDocument(
            id = "doc-1",
            title = "网络基础 | 入门 😊",
            format = StudyDocumentFormat.Markdown,
            sourceName = "notes/网络基础.md",
            folderLabel = "学习资料 | 计算机网络",
            snapshotId = "snapshot-1",
            relativePath = "notes/网络基础.md",
            sizeBytes = 1234L,
            sourceModifiedAtEpochMillis = 100L,
            importedAtEpochMillis = 200L,
            contentHash = "abc123",
            version = 1,
            lastReadAtEpochMillis = 300L,
            scrollY = 460,
            progress = 0.42f,
            category = StudyCategory.Vocabulary,
        )

        assertEquals(document, decodeStudyDocument(encodeStudyDocument(document)))
    }

    @Test
    fun versionOneDocumentDefaultsToNotesCategory() {
        val current = encodeStudyDocument(
            StudyDocument(
                id = "legacy-doc",
                title = "旧资料",
                format = StudyDocumentFormat.Markdown,
                sourceName = "legacy.md",
                folderLabel = "旧文件夹",
                snapshotId = "legacy-snapshot",
                relativePath = "legacy.md",
                sizeBytes = 12L,
                sourceModifiedAtEpochMillis = 10L,
                importedAtEpochMillis = 20L,
                contentHash = "legacy-hash",
                category = StudyCategory.Vocabulary,
            ),
        ).split('|')
        val legacy = (listOf("1") + current.drop(1).dropLast(1)).joinToString("|")

        assertEquals(StudyCategory.Notes, decodeStudyDocument(legacy)?.category)
    }

    @Test
    fun progressIsClampedWhenStoredValueIsOutOfRange() {
        val document = StudyDocument(
            id = "doc-2",
            title = "标题",
            format = StudyDocumentFormat.Markdown,
            sourceName = "lesson.md",
            folderLabel = "单文件",
            snapshotId = "snapshot-2",
            relativePath = "lesson.html",
            sizeBytes = 1L,
            sourceModifiedAtEpochMillis = 0L,
            importedAtEpochMillis = 0L,
            contentHash = "hash",
            progress = 1f,
        )
        val encoded = encodeStudyDocument(document).replace("|1.0|", "|2.0|")

        assertEquals(1f, decodeStudyDocument(encoded)?.progress)
    }

    @Test
    fun malformedDocumentIsIgnored() {
        assertNull(decodeStudyDocument(""))
        assertNull(decodeStudyDocument("1|doc|Unknown|snapshot"))
    }
}
