package com.example.campuslife.ui.study

import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMarkdownRendererTest {

    private val tableMarkdown = """
        | English | 中文 |
        | --- | --- |
        | apple | 苹果 |
        | pear | 梨 |
    """.trimIndent()

    @Test
    fun vocabularyModeInjectsFullCellMaskBehavior() {
        val html = renderMarkdown(
            source = tableMarkdown,
            css = "<style></style>",
            enableVocabularyTableMask = true,
        ).html

        assertTrue(html.contains("study-vocabulary-table-mask-script"))
        assertTrue(html.contains("Array.from(table.rows).slice(1)"))
        assertTrue(html.contains("inset: 0"))
        // 遮罩底色跟随正文底色（--study-canvas 由阅读器样式注入），兜底仍是白
        assertTrue(html.contains("background-color: var(--study-canvas, #fff)"))
        assertTrue(html.contains("cell.classList.toggle(REVEALED)"))
    }

    @Test
    fun regularMarkdownDoesNotReceiveVocabularyMaskBehavior() {
        val html = renderMarkdown(
            source = tableMarkdown,
            css = "<style></style>",
            enableVocabularyTableMask = false,
        ).html

        assertFalse(html.contains("study-vocabulary-table-mask-script"))
        assertFalse(html.contains("study-vocabulary-mask-cell"))
    }

    @Test
    fun tableMaskIsAvailableOnlyForVocabularyMarkdown() {
        val vocabularyDocument = StudyDocumentUiModel(
            id = "words",
            title = "Words",
            format = StudyDocumentFormat.Markdown,
            sourceName = "words.md",
            folderLabel = "English",
            detailLabel = "1 KB",
            snapshotId = "snapshot",
            relativePath = "words.md",
            category = StudyCategory.Vocabulary,
        )

        assertTrue(vocabularyDocument.supportsVocabularyTableMask)
        assertFalse(
            vocabularyDocument.copy(category = StudyCategory.Notes).supportsVocabularyTableMask,
        )
        assertFalse(
            vocabularyDocument.copy(category = StudyCategory.Documents).supportsVocabularyTableMask,
        )
    }
}
