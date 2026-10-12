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
            vocabularyMask = "0:1:abc123:2,5",
            taskCheckboxState = "v1\nabcdef123456\n0;3;0;9f3a1c229f3a",
        )

        assertEquals(document, decodeStudyDocument(encodeStudyDocument(document)))
    }

    @Test
    fun versionThreeDocumentDefaultsToNoTaskCheckboxState() {
        val current = encodeStudyDocument(
            StudyDocument(
                id = "checked-doc",
                title = "单词表",
                format = StudyDocumentFormat.Markdown,
                sourceName = "words.md",
                folderLabel = "英语",
                snapshotId = "checked-snapshot",
                relativePath = "words.md",
                sizeBytes = 12L,
                sourceModifiedAtEpochMillis = 10L,
                importedAtEpochMillis = 20L,
                contentHash = "checked-hash",
                category = StudyCategory.Vocabulary,
                vocabularyMask = "0:1:checked-hash:2",
                taskCheckboxState = "v1\nabcdef123456\n0;1;0;9f3a1c229f3a",
            ),
        ).split('|')
        // v3 比当前编码少了尾部的复选框状态一段，正好是 18 段。
        val legacy = (listOf("3") + current.drop(1).dropLast(1)).joinToString("|")

        val restored = decodeStudyDocument(legacy)

        assertEquals(StudyCategory.Vocabulary, restored?.category)
        assertEquals("0:1:checked-hash:2", restored?.vocabularyMask)
        assertEquals(null, restored?.taskCheckboxState)
    }

    @Test
    fun versionTwoDocumentDefaultsToNoVocabularyMask() {
        val current = encodeStudyDocument(
            StudyDocument(
                id = "masked-doc",
                title = "单词本",
                format = StudyDocumentFormat.Markdown,
                sourceName = "words.md",
                folderLabel = "英语",
                snapshotId = "masked-snapshot",
                relativePath = "words.md",
                sizeBytes = 12L,
                sourceModifiedAtEpochMillis = 10L,
                importedAtEpochMillis = 20L,
                contentHash = "masked-hash",
                category = StudyCategory.Vocabulary,
                vocabularyMask = "0:1:masked-hash:2",
            ),
        ).split('|')
        val legacy = (listOf("2") + current.drop(1).dropLast(2)).joinToString("|")

        val restored = decodeStudyDocument(legacy)

        assertEquals(StudyCategory.Vocabulary, restored?.category)
        assertEquals(null, restored?.vocabularyMask)
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
        // v1 比当前编码少了尾部的分类、遮挡状态和复选框状态三段，正好是 16 段。
        val legacy = (listOf("1") + current.drop(1).dropLast(3)).joinToString("|")

        assertEquals(StudyCategory.Notes, decodeStudyDocument(legacy)?.category)
        assertEquals(null, decodeStudyDocument(legacy)?.vocabularyMask)
        assertEquals(null, decodeStudyDocument(legacy)?.taskCheckboxState)
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
