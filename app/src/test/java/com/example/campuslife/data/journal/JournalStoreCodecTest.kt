package com.example.campuslife.data.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class JournalStoreCodecTest {

    @Test
    fun encodeDecodeRoundTripPreservesUnifiedContent() {
        val entry = JournalEntry(
            id = "entry-1",
            content = "标题 | 有分隔符\n第二行：中文、emoji 😊 和控制符号 ; , |",
            categoryId = "category-with-|字符",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        )

        assertEquals(entry, decodeJournalEntry(encodeJournalEntry(entry)))
    }

    @Test
    fun firstLineIsTitleAndRemainingLinesAreBody() {
        val entry = JournalEntry(
            id = "entry-2",
            content = "第一行标题\n正文第一行\n正文第二行",
            categoryId = "notes",
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 10L,
        )

        assertEquals("第一行标题", entry.title)
        assertEquals("正文第一行\n正文第二行", entry.body)
    }

    @Test
    fun legacyEntryMigratesTitleBodyAndCategory() {
        val legacy = listOf(
            "old-entry",
            "10",
            "20",
            "Reading",
            "1",
            encodedTextForTest("平静"),
            encodedTextForTest("旧标题"),
            encodedTextForTest("旧正文"),
        ).joinToString("|")

        assertEquals(
            JournalEntry(
                id = "old-entry",
                content = "旧标题\n旧正文",
                categoryId = "legacy-reading",
                createdAtEpochMillis = 10L,
                updatedAtEpochMillis = 20L,
            ),
            decodeJournalEntry(legacy),
        )
    }

    @Test
    fun categoryRoundTripPreservesUserName() {
        val category = JournalCategory(
            id = "category-1",
            name = "读书 | 随想 😊",
            createdAtEpochMillis = 123L,
        )

        assertEquals(category, decodeJournalCategory(encodeJournalCategory(category)))
    }

    @Test
    fun malformedEntriesAndCategoriesAreIgnored() {
        assertNull(decodeJournalEntry(""))
        assertNull(decodeJournalEntry("id|bad|data|with|wrong|fields"))
        assertNull(decodeJournalEntry("id|1|2|MissingCategory|0||||"))
        assertNull(decodeJournalCategory("bad|category"))
    }

    private fun encodedTextForTest(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
}
