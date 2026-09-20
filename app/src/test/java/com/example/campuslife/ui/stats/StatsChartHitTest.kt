package com.example.campuslife.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图表点选的落点换算。
 *
 * 统计页有两张图，方向正好相反：柱状图一天一列（看横向落点），甘特图一天一行（看纵向落点）。
 * 这两份换算曾经共用过一份，结果点甘特图某一天会跳到别的日期上——这几个测试就是钉住这件事。
 */
class StatsChartHitTest {

    private val rowHeight = 22f
    private val rowGap = 4f
    private val slot = rowHeight + rowGap

    private fun centerOf(row: Int): Float = row * slot + rowHeight / 2f

    @Test
    fun ganttPicksTheRowUnderTheFinger() {
        repeat(7) { row ->
            assertEquals(row, ganttRowIndexAt(centerOf(row), rowHeight, rowGap, 7))
        }
    }

    @Test
    fun ganttRowBoundariesBelongToTheRowTheyStart() {
        assertEquals(0, ganttRowIndexAt(0f, rowHeight, rowGap, 7))
        assertEquals(0, ganttRowIndexAt(slot - 0.1f, rowHeight, rowGap, 7))
        assertEquals(1, ganttRowIndexAt(slot, rowHeight, rowGap, 7))
        assertEquals(2, ganttRowIndexAt(slot * 2, rowHeight, rowGap, 7))
    }

    @Test
    fun ganttClampsToTheLastRowBelowTheChart() {
        assertEquals(6, ganttRowIndexAt(slot * 6 + rowHeight, rowHeight, rowGap, 7))
        assertEquals(6, ganttRowIndexAt(10_000f, rowHeight, rowGap, 7))
        assertEquals(0, ganttRowIndexAt(-20f, rowHeight, rowGap, 7))
    }

    /**
     * 回归：甘特图不能按横向落点选日子。
     *
     * 近 30 天时行高被压到下限 10dp，最后一行的中心也必须能选中，
     * 否则窗口里最新的几天（含今天）就永远点不到。
     */
    @Test
    fun ganttKeepsItsDirectionOnACompressedChart() {
        val compressedRowHeight = 10f
        val compressedSlot = compressedRowHeight + rowGap
        assertEquals(29, ganttRowIndexAt(compressedSlot * 29 + 5f, compressedRowHeight, rowGap, 30))
        assertEquals(0, ganttRowIndexAt(5f, compressedRowHeight, rowGap, 30))

        // 同一个落点用横向换算会得到别的一天——这就是两者不能共用一份的原因。
        assertTrue(barColumnIndexAt(300f, 320f / 7, 7) != 4)
    }

    @Test
    fun barChartPicksTheColumnUnderTheFinger() {
        val columnWidth = 320f / 7
        repeat(7) { column ->
            val x = column * columnWidth + columnWidth / 2f
            assertEquals(column, barColumnIndexAt(x, columnWidth, 7))
        }
        assertEquals(6, barColumnIndexAt(1_000f, columnWidth, 7))
        assertEquals(0, barColumnIndexAt(-5f, columnWidth, 7))
    }

    @Test
    fun degenerateSizesDoNotCrashOrJumpOffTheEnd() {
        assertEquals(0, ganttRowIndexAt(50f, rowHeight, rowGap, 0))
        assertEquals(0, ganttRowIndexAt(50f, 0f, 0f, 7))
        assertEquals(0, barColumnIndexAt(50f, 0f, 7))
        assertEquals(0, barColumnIndexAt(50f, 10f, 0))
    }
}
