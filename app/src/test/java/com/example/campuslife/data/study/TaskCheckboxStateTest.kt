package com.example.campuslife.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCheckboxStateTest {

    private val sourceHash = "abcdef123456"
    private val otherHash = "abcdef654321"

    @Test
    fun encodedMarksRoundTripInAStableOrder() {
        val state = TaskCheckboxState(
            sourceHash = sourceHash,
            marks = setOf(
                TaskCheckboxMark(0, 3, 0, "9f3a1c229f3a"),
                TaskCheckboxMark(1, 2, 0, "41d0bb7e41d0"),
                TaskCheckboxMark(LIST_ITEM_TABLE_INDEX, 2, -1, "a7c31e05a7c3"),
            ),
        )

        val encoded = encodeTaskCheckboxState(state)

        assertEquals(
            """
            v1
            abcdef123456
            -1;2;-1;a7c31e05a7c3
            0;3;0;9f3a1c229f3a
            1;2;0;41d0bb7e41d0
            """.trimIndent(),
            encoded,
        )
        assertEquals(state, decodeTaskCheckboxState(encoded))
    }

    @Test
    fun stateWithoutMarksStillPersistsTheExplicitAllUncheckedChoice() {
        val encoded = encodeTaskCheckboxState(TaskCheckboxState(sourceHash, emptySet()))

        assertEquals("v1\n$sourceHash", encoded)
        assertEquals(
            TaskCheckboxState(sourceHash = sourceHash, marks = emptySet()),
            decodeTaskCheckboxState(encoded),
        )
        assertNull(encodeTaskCheckboxState(TaskCheckboxState("", setOf(TaskCheckboxMark(0, 1, 0, sourceHash)))))
    }

    @Test
    fun marksWithUnusableCoordinatesAreDroppedInsteadOfFailingTheWholeSet() {
        val decoded = decodeTaskCheckboxState(
            """
            v1
            $sourceHash
            0;3;0;9f3a1c229f3a
            0|x|0|41d0bb7e41d0
            0|-1|0|41d0bb7e41d0
            0|3|0|short
            0|3
            """.trimIndent(),
        )

        assertEquals(setOf(TaskCheckboxMark(0, 3, 0, "9f3a1c229f3a")), decoded?.marks)
    }

    @Test
    fun malformedStateIsIgnored() {
        assertNull(decodeTaskCheckboxState(null))
        assertNull(decodeTaskCheckboxState(""))
        assertNull(decodeTaskCheckboxState("v1"))
        assertNull(decodeTaskCheckboxState("v2\n$sourceHash\n0;1;0;9f3a1c229f3a"))
        assertNull(decodeTaskCheckboxState("v1\nshort\n0;1;0;9f3a1c229f3a"))
        assertNull(decodeTaskCheckboxState("v1\nabcde|123456\n0;1;0;9f3a1c229f3a"))
    }

    @Test
    fun nonHexItemHashesAreDroppedSoStateCannotBreakTheDocumentCodec() {
        val decoded = decodeTaskCheckboxState(
            "v1\n$sourceHash\n0;1;0;9f3a1c229f3a\n0;2;0;bad|hash0000",
        )

        assertEquals(
            setOf(TaskCheckboxMark(0, 1, 0, "9f3a1c229f3a")),
            decoded?.marks,
        )
    }

    @Test
    fun stateExpiresWhenTheDocumentContentChanged() {
        val state = TaskCheckboxState(sourceHash = sourceHash, marks = emptySet())

        assertTrue(state.matchesSource("abcdef1234567890"))
        assertFalse(state.matchesSource(otherHash))
        assertFalse(state.matchesSource(""))
    }

    @Test
    fun onlyTheMarkWhoseTextChangedStopsMatching() {
        val moved = TaskCheckboxMark(0, 3, 0, "9f3a1c229f3a")
        val edited = TaskCheckboxMark(0, 4, 0, "41d0bb7e41d0")

        assertEquals(moved, decodeTaskCheckboxState(encodeTaskCheckboxState(TaskCheckboxState(sourceHash, setOf(moved)))!!)?.marks?.first())
        assertTrue(edited.itemHash != moved.itemHash)
    }

    @Test
    fun encodedStateNeverCollidesWithTheDocumentFieldSeparator() {
        val state = TaskCheckboxState(
            sourceHash = sourceHash,
            marks = setOf(TaskCheckboxMark(0, 3, 0, "9f3a1c229f3a")),
        )

        val document = StudyDocument(
            id = "doc-1",
            title = "单词表",
            format = StudyDocumentFormat.Markdown,
            sourceName = "words.md",
            folderLabel = "英语",
            snapshotId = "snapshot-1",
            relativePath = "words.md",
            sizeBytes = 12L,
            sourceModifiedAtEpochMillis = 10L,
            importedAtEpochMillis = 20L,
            contentHash = sourceHash,
            category = StudyCategory.Vocabulary,
            taskCheckboxState = encodeTaskCheckboxState(state),
        )

        // 资料索引按 `|` 分段，复选框状态整体只是其中一段：出现同名字符就会多切出几段，
        // 整条资料随之解码为空。
        assertEquals(document, decodeStudyDocument(encodeStudyDocument(document)))
    }

    @Test
    fun itemHashIgnoresRenderingNoiseButKeepsTheText() {
        val hash = taskItemHash("appeal")
        val noisy = taskItemHash("\u200B appeal \u200B")

        assertEquals(HASH_CHAR_COUNT, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
        assertEquals(hash, noisy)
        assertTrue(hash != taskItemHash("cultivate"))
        assertTrue(hash != taskItemHash("appeal 呼吁"))
    }
}
