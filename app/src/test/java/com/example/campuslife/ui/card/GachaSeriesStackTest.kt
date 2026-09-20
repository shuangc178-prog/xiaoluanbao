package com.example.campuslife.ui.card

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页那叠卡的翻页口径。
 *
 * 只有一条规则，但很容易写错方向或漏掉边界：滑动后停在第几弹。
 * 这里把「滑到头就停住」钉死——绕回另一端会让用户滑一下就到最远的弹，很难找回来。
 */
class GachaSeriesStackTest {

    private val count = 5

    @Test
    fun swipeMovesOneSeriesAtATime() {
        assertEquals(1, moveSeriesIndex(current = 0, step = 1, count = count))
        assertEquals(2, moveSeriesIndex(current = 1, step = 1, count = count))
        assertEquals(1, moveSeriesIndex(current = 2, step = -1, count = count))
    }

    @Test
    fun endsAreStickyAndDoNotWrapAround() {
        assertEquals(0, moveSeriesIndex(current = 0, step = -1, count = count))
        assertEquals(4, moveSeriesIndex(current = 4, step = 1, count = count))
        assertEquals(0, moveSeriesIndex(current = 0, step = -3, count = count))
    }

    @Test
    fun zeroStepKeepsTheCurrentSeries() {
        assertEquals(3, moveSeriesIndex(current = 3, step = 0, count = count))
    }

    @Test
    fun singleSeriesNeverMoves() {
        assertEquals(0, moveSeriesIndex(current = 0, step = 1, count = 1))
        assertEquals(0, moveSeriesIndex(current = 0, step = -1, count = 1))
    }

    @Test
    fun anOutOfRangeIndexIsPulledBackIn() {
        assertEquals(4, moveSeriesIndex(current = 9, step = 0, count = count))
        assertEquals(0, moveSeriesIndex(current = -3, step = 0, count = count))
        assertEquals(0, moveSeriesIndex(current = 0, step = 1, count = 0))
    }
}
