package com.example.campuslife.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyTaskStateTest {

    @Test
    fun encodeDecodeRoundTripPreservesCheckedIndexes() {
        val state = StudyTaskState(
            documentId = "doc-1",
            checkedIndexes = setOf(0, 3, 7),
            sourceFingerprint = "hash:notes/CET-6/Day-2.md",
        )

        assertEquals(state, decodeStudyTaskState(encodeStudyTaskState(state)))
    }

    @Test
    fun malformedStateIsIgnored() {
        assertNull(decodeStudyTaskState(""))
        assertNull(decodeStudyTaskState("1|only-two-fields"))
        assertNull(decodeStudyTaskState("9|YWJj||YWJj"))
    }

    @Test
    fun toggleAddsAndRemovesIndex() {
        val state = StudyTaskState(documentId = "doc", sourceFingerprint = "hash:doc.md")

        val checked = state.toggled(2, true)
        assertEquals(setOf(2), checked.checkedIndexes)
        assertEquals(emptySet<Int>(), checked.toggled(2, false).checkedIndexes)
        // 负数下标来自异常页面回调，直接忽略，不能污染记录。
        assertEquals(state, state.toggled(-1, true))
    }

    @Test
    fun fingerprintTracksTheImportedRevision() {
        val document = document(contentHash = "hash-a", relativePath = "notes/a.md")
        val reimported = document(contentHash = "hash-b", relativePath = "notes/a.md")

        assertEquals("hash-a:notes/a.md", studyTaskStateFingerprint(document))
        assertEquals(
            false,
            studyTaskStateFingerprint(document) == studyTaskStateFingerprint(reimported),
        )
    }

    @Test
    fun markdownTasksAreCountedInSourceOrder() {
        // 刻意不用 trimIndent：嵌套列表靠缩进区分，缩进被抹掉就测不出层级了。
        val source = listOf(
            "- [ ] apple",
            "- [x] pear",
            "1. [X] plum",
            "  - [ ] nested",
            "普通段落 [ ] 不算任务",
            "- [] 缺空格不算任务",
        ).joinToString("\n")

        assertEquals(listOf(false, true, true, false), parseMarkdownTaskChecks(source))
        assertEquals(setOf(1, 2), markdownCheckedIndexes(source))
    }

    @Test
    fun checkboxRowsInATableAreNotTaskListItems() {
        // CET-6 单词表的 `[ ]` 在表格单元格里，不属于任务列表扩展的语法。
        val source = "| 完成 | English |\n| --- | --- |\n| [ ] | shallow |"

        assertEquals(emptyList<Boolean>(), parseMarkdownTaskChecks(source))
    }

    @Test
    fun checkedStateIsXorOfMarkdownAndStoredOverlay() {
        val source = """
            - [ ] apple
            - [x] pear
            - [ ] plum
        """.trimIndent()

        // 正文里只有 pear（下标 1）是勾上的。
        assertEquals(setOf(1), effectiveCheckedTaskIndexes(source, emptySet()))
        // 覆盖层翻转 apple 之后，apple 变勾上、pear 保持勾上。
        assertEquals(setOf(0, 1), effectiveCheckedTaskIndexes(source, setOf(0)))
        // 翻转 pear 得到「取消勾选」：异或而不是并集才有这个效果。
        assertEquals(setOf(0), effectiveCheckedTaskIndexes(source, setOf(0, 1)))
        assertEquals(emptySet<Int>(), effectiveCheckedTaskIndexes(source, setOf(1)))
    }

    private fun document(contentHash: String, relativePath: String) = StudyDocument(
        id = "doc",
        title = "Day 2",
        format = StudyDocumentFormat.Markdown,
        sourceName = relativePath,
        folderLabel = "CET-6",
        snapshotId = "snapshot",
        relativePath = relativePath,
        sizeBytes = 10L,
        sourceModifiedAtEpochMillis = 0L,
        importedAtEpochMillis = 0L,
        contentHash = contentHash,
    )
}
