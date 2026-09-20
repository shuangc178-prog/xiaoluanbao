package com.example.campuslife.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyOutlineTest {

    @Test
    fun collectsHeadingsInDocumentOrderWithLevels() {
        val source = """
            # 一级标题
            正文
            ## 二级标题
            ### 三级标题
            #### 四级标题
            ## 另一个二级
        """.trimIndent()

        val outline = parseMarkdownOutline(source)

        assertEquals(
            listOf(
                Triple(1, "一级标题", "一级标题"),
                Triple(2, "二级标题", "二级标题"),
                Triple(3, "三级标题", "三级标题"),
                Triple(4, "四级标题", "四级标题"),
                Triple(2, "另一个二级", "另一个二级"),
            ),
            outline.map { Triple(it.level, it.title, it.anchor) },
        )
    }

    @Test
    fun skipsCodeFencesButKeepsBlockquoteHeadings() {
        val source = """
            # 真标题
            ```md
            # 代码里的标题
            ## 也是代码
            ```
            > # 引用里的标题
            ## 又一个真标题
        """.trimIndent()

        val outline = parseMarkdownOutline(source)

        // 围栏代码块里的 `#` 不是标题，要跳过；引用块里的标题会被 commonmark
        // 同样渲染出来，目录必须与正文对齐，因此保留。
        assertEquals(
            listOf("真标题", "引用里的标题", "又一个真标题"),
            outline.map { it.title },
        )
    }

    @Test
    fun deduplicatesRepeatedTitlesWithSuffix() {
        val source = """
            # 相同
            # 相同
            # 相同
        """.trimIndent()

        val anchors = parseMarkdownOutline(source).map { it.anchor }

        assertEquals(3, anchors.size)
        assertEquals(anchors.size, anchors.distinct().size)
        assertEquals("相同", anchors[0])
        assertTrue(anchors[1].startsWith("相同-"))
    }

    @Test
    fun stripsInlineDecorationFromTitleText() {
        val source = """
            ## 带 `代码` 与 **加粗** 的标题
            ### [链接文字](https://example.com)
        """.trimIndent()

        val outline = parseMarkdownOutline(source)

        assertEquals(listOf("带 代码 与 加粗 的标题", "链接文字"), outline.map { it.title })
    }
}
