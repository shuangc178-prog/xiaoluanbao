package com.example.campuslife.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日记编辑器的自适应滑动口径（第 16.18 / 16.20 / 16.22 节）。
 *
 * 场景取真机实测到的一组数：编辑区可视高度 819px、正文行高 90px、书写行余量 16px、
 * 光标下方保留 7 行。两条口径：
 * ① 前 7 行走「文字从顶部往下排」，到第 8 行才开始把页面抬起来；
 * ② 每次换行只越界一行，页面就只向上走一行行高，光标停在第 8 行不再移动。
 *
 * 基准是**编辑区高度**，不是键盘 inset：`WindowInsets.ime` 在「键盘盖住窗口」的机型上
 * 会落在可视区之外，用它会退化成「写到最后一两行才滚」——那正是用户实测到的问题。
 */
class JournalEditorScrollTest {

    /** 键盘收起时的可视高度：能放下 18 行，7 行预留可以完整保留。 */
    private val viewportHeight = 844
    private val lineHeight = 46
    private val clearance = 16
    private val linesBelow = 7

    /** 书写行下边界：可视区底部往上 7 行。 */
    private val writingLine = viewportHeight - linesBelow * lineHeight

    /** 光标在书写行上时，它的行盒顶边所在的位置（书写行往上一个余量）。 */
    private val parkedCursorTop = writingLine - clearance

    /** 第 8 行（光标开始把页面抬起来的那一行）的行盒顶边。 */
    private val eighthLineTop = writingLine - clearance + 1

    private fun delta(
        drawTopPx: Int,
        currentScrollPx: Int = 0,
        viewport: Int = viewportHeight,
    ) = cursorScrollDelta(
        drawTopPx = drawTopPx,
        currentScrollPx = currentScrollPx,
        editorViewportHeightPx = viewport,
        cursorClearancePx = clearance,
        lineHeightPx = lineHeight,
        linesKeptBelowCursor = linesBelow,
    )

    @Test
    fun writingLineLeavesSevenLinesBelowTheCursor() {
        assertEquals(
            viewportHeight - linesBelow * lineHeight,
            writingLineBottomPx(
                editorViewportHeightPx = viewportHeight,
                lineHeightPx = lineHeight,
                linesKeptBelowCursor = linesBelow,
            ),
        )
    }

    @Test
    fun firstSevenLinesDoNotScroll() {
        // 第 1~7 行：文字从顶部一行行往下排（第 1 行顶边就是可视区顶部），页面一动不动。
        for (line in 1..linesBelow) {
            val drawTopPx = (line - 1) * lineHeight
            assertEquals("第 $line 行不该滚", 0, delta(drawTopPx = drawTopPx))
        }
    }

    @Test
    fun eighthLineStartsTheScroll() {
        // 第 8 行越过书写行：从这里开始页面才抬起来，起点抬起量是 1。
        assertEquals(1, delta(drawTopPx = eighthLineTop))
    }

    @Test
    fun eachFollowingLineMovesThePageUpByOneLine() {
        // 第 9、10 行：每多一行，页面恰好再上移一行行高。
        assertEquals(1 + lineHeight, delta(drawTopPx = eighthLineTop + lineHeight))
        assertEquals(1 + 2 * lineHeight, delta(drawTopPx = eighthLineTop + 2 * lineHeight))
    }

    @Test
    fun cursorParksOnTheWritingLineAndStops() {
        // 滚过之后同一条行位置不再继续滚：光标停在第 8 行。
        assertEquals(0, delta(drawTopPx = eighthLineTop, currentScrollPx = 1))
        assertEquals(0, delta(drawTopPx = eighthLineTop + lineHeight, currentScrollPx = 1 + lineHeight))
    }

    @Test
    fun scrollAlwaysLandsTheCursorJustAboveTheWritingLine() {
        // 不变量：不管越界多少（换行一行、粘贴整段），滚完光标都停在书写行上方一个余量处。
        for (linesOverflow in 1..20) {
            val drawTopPx = eighthLineTop + linesOverflow * lineHeight
            val applied = delta(drawTopPx = drawTopPx)
            assertEquals(
                "越界 $linesOverflow 行后光标应停在书写行上方",
                parkedCursorTop,
                drawTopPx - applied,
            )
        }
    }

    @Test
    fun firstLineTitleScrollsByTheTitleLineHeight() {
        // 首行是标题：行高比正文高，滚动量同样等于越界的量。
        val titleLineHeight = 62
        assertEquals(
            titleLineHeight,
            delta(drawTopPx = eighthLineTop + titleLineHeight) - delta(drawTopPx = eighthLineTop),
        )
    }

    @Test
    fun cursorAboveTheViewportIsBroughtBack() {
        // 长按删除或返回上一行，光标跑到视口上方：回滚到书写行位置（负增量）。
        assertEquals(100 - clearance - 400, delta(drawTopPx = 100, currentScrollPx = 400))
    }

    @Test
    fun pastedBlockScrollsByTheWholeOverflow() {
        // 粘贴整段时越界很多：一次滚到书写行，不是只挪一行。
        val pasteOverflow = 12 * lineHeight
        val drawTopPx = eighthLineTop + pasteOverflow
        val applied = delta(drawTopPx = drawTopPx)
        assertEquals(parkedCursorTop, drawTopPx - applied)
        assertTrue("越界一大段就该滚一大段", applied > lineHeight)
    }

    @Test
    fun tallerViewportKeepsTheSameSevenLineReserve() {
        // 可视区更高（键盘没弹出 / 屏幕更大）时，落笔位置跟着抬高，保留的行数不变。
        val tallViewport = 1600
        assertEquals(
            tallViewport - linesBelow * lineHeight,
            writingLineBottomPx(
                editorViewportHeightPx = tallViewport,
                lineHeightPx = lineHeight,
                linesKeptBelowCursor = linesBelow,
            ),
        )
    }

    @Test
    fun shortViewportFoldsTheReserveSoTheCursorStaysVisible() {
        // 键盘弹出后可视区只剩 294px（实测值）：7 行 = 322px 放不下，
        // 折半保留 147px，光标停在视口内，而不是被顶到视口之上。
        val keyboardViewport = 294
        val line = writingLineBottomPx(
            editorViewportHeightPx = keyboardViewport,
            lineHeightPx = lineHeight,
            linesKeptBelowCursor = linesBelow,
        )
        assertEquals(keyboardViewport - keyboardViewport / 2, line)
        assertTrue("光标必须落在视口内", line - clearance >= 0)
        assertTrue("光标上方至少留得下一行", line - clearance >= lineHeight)
    }

    @Test
    fun viewportTooShortForEvenOneLineKeepsTheCursorAtTheTop() {
        // 极端情况：可视区只放得下一行，也不能把落笔位置算成负数。
        assertEquals(
            lineHeight,
            writingLineBottomPx(
                editorViewportHeightPx = lineHeight,
                lineHeightPx = lineHeight,
                linesKeptBelowCursor = linesBelow,
            ),
        )
    }

    @Test
    fun emptyTextHintNeverMapsOffsetsBeyondTheOriginalText() {
        // 空笔记的提示比原文长，两个方向都必须收敛到 0：
        // 恒等映射会报 1 -> 1 越界，Compose 校验直接抛异常（编辑器一打开就闪退）。
        for (offset in 0..20) {
            assertEquals(0, EmptyTextHintOffsetMapping.originalToTransformed(offset))
            assertEquals(0, EmptyTextHintOffsetMapping.transformedToOriginal(offset))
        }
    }
}
