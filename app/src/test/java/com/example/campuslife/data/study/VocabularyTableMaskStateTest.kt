package com.example.campuslife.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTableMaskStateTest {

    @Test
    fun encodedMaskRoundTripsWithRevealedRowsSorted() {
        val state = VocabularyTableMaskState(
            tableIndex = 1,
            columnIndex = 2,
            sourceHash = "abc123",
            revealedRows = setOf(5, 3, 9),
        )

        val decoded = decodeVocabularyTableMask(encodeVocabularyTableMask(state))

        assertEquals(state, decoded)
        assertEquals("1:2:abc123:3,5,9", encodeVocabularyTableMask(state))
    }

    @Test
    fun maskWithoutRevealedRowsKeepsAnEmptyTrailingSection() {
        val state = VocabularyTableMaskState(
            tableIndex = 0,
            columnIndex = 1,
            sourceHash = "hash",
            revealedRows = emptySet(),
        )

        assertEquals("0:1:hash:", encodeVocabularyTableMask(state))
        assertEquals(state, decodeVocabularyTableMask("0:1:hash:"))
    }

    @Test
    fun malformedMaskIsIgnored() {
        assertNull(decodeVocabularyTableMask(null))
        assertNull(decodeVocabularyTableMask(""))
        assertNull(decodeVocabularyTableMask("0:1:hash"))
        assertNull(decodeVocabularyTableMask("-1:1:hash:"))
        assertNull(decodeVocabularyTableMask("0:-1:hash:"))
        assertNull(decodeVocabularyTableMask("0:1::5"))
        assertNull(decodeVocabularyTableMask("x:1:hash:"))
    }

    @Test
    fun unusableRowIndicesAreDroppedInsteadOfFailingTheWholeMask() {
        val decoded = decodeVocabularyTableMask("0:1:hash: 2 ,x,-3,4")

        assertEquals(setOf(2, 4), decoded?.revealedRows)
    }

    @Test
    fun maskExpiresWhenTheDocumentContentChanged() {
        val state = VocabularyTableMaskState(0, 1, "abc123", setOf(2))

        assertTrue(state.matchesSource("abc123"))
        assertTrue(state.matchesSource("abc123def456"))
        assertFalse(state.matchesSource("abc124"))
        assertFalse(state.matchesSource(""))
    }
}
