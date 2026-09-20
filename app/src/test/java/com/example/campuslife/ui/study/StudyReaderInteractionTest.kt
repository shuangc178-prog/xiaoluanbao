package com.example.campuslife.ui.study

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读器的两种交互：Typora 注释点开看、任务列表点得动。
 *
 * 用的样本直接抄自 `F:\Basic knowledge` 里的真实笔记，注释正文保持原样（base64url 编码后
 * 仍然只含字母数字与 `-`、`_`），一旦解码链路被改坏，这里会立刻红。
 */
class StudyReaderInteractionTest {

    private fun render(source: String) = renderMarkdown(
        source = source,
        css = "<style></style>",
        enableVocabularyTableMask = false,
    ).html

    @Test
    fun noteInsideHighlightKeepsItsTextAndBecomesClickable() {
        val html = render(
            "==端口<!--typora-annotation-v1-aff473babc935c259c20fd0b-" +
                "56uv5Y+j5piv5pW05pWwLOWPluWAvOiMg+WbtOWcqDAtNjU1MzXkuYvpl7Q-->==是在ip地址上的入口",
        )

        // 高亮文字仍然在正文里，并且换成了可点的注释元素
        assertTrue(html, html.contains("端口"))
        assertTrue(html, html.contains("class=\"study-note\""))
        // 备注正文以 base64url 落在 data 属性上（避免被 commonmark 二次转义；
        // 原文里的 `+`、`/` 会编码成 `-`、`_`，页面脚本解码时再换回来）
        assertTrue(html, html.contains("data-study-note="))
        assertTrue(html, html.contains("56uv5Y-j5piv5pW05pWw"))
        // 元数据本身不能再出现在正文里
        assertFalse(html, html.contains("typora-annotation-v1"))
    }

    @Test
    fun noteTextIsDecodedWithUtf8() {
        // 「也就是俗称的网址」——解码结果必须能还原成中文，而不是乱码或空串。
        val html = render(
            "IP 地址<!--typora-annotation-v1-8e4bae27955737fc72b7f561-" +
                "5Lmf5bCx5piv5L+X56ew55qE572R5Z2A-->就是网址",
        )

        // 属性里是 base64url，原文里的 `+` 会变成 `-`，页面脚本解码时再换回来
        assertTrue(html, html.contains("data-study-note="))
        assertTrue(html, html.contains("5Lmf5bCx5piv5L-X56ew55qE572R5Z2A"))
    }

    @Test
    fun orphanNoteOnItsOwnParagraphIsKept() {
        // 单独占一行的注释没有高亮包裹，同样不能变成看不见的 HTML 注释。
        val html = render(
            "<!--typora-annotation-v1-2b00757537176145e4e5d2cd-" +
                "5L2/55So4oCd77yM4oCc5Lya5Lqn55Sf56m65qC85Zug5Li66buY6K6kc2VwPSIgIu+8jOWPr+S7peaUuXNlcD0iIuadpea2iOmZpOepuuagvA-->",
        )

        assertTrue(html, html.contains("study-note-orphan"))
        assertTrue(html, html.contains("data-study-note="))
        assertFalse(html, html.contains("typora-annotation-v1"))
    }

    @Test
    fun typoraColorMarkerBecomesInlineColorOnlyForSafeValues() {
        val colored = render("==登录操作<!--typora-color-v1-dc8b5d0002-#c62828-none-->==的说明")

        assertTrue(colored, colored.contains("<mark style=\"color: #c62828\">"))
        assertTrue(colored, colored.contains("登录操作"))
        assertFalse(colored, colored.contains("typora-color-v1"))

        // 非 `#rrggbb` 的取值一律不落样式，避免把任意 CSS 引进文档。
        val unsafe = render("==文字<!--typora-color-v1-abc123-#c62828;background:url(x)-none-->==")

        assertFalse(unsafe, unsafe.contains("style="))
    }

    @Test
    fun taskListItemsRenderAsRealCheckboxes() {
        val html = render(
            """
            - [x] shallow 浅的
            - [ ] gloomy 阴沉的
            """.trimIndent(),
        )

        // commonmark 的任务列表扩展会产出真复选框，但默认是 disabled 的；
        // 已勾选项带 checked，未勾选项不带——页面脚本负责解禁并按覆盖层校正。
        assertTrue(html, html.contains("<input type=\"checkbox\" disabled=\"\" checked=\"\">"))
        assertTrue(html, html.contains("<input type=\"checkbox\" disabled=\"\">"))
    }

    @Test
    fun readerScriptIsAlwaysPresentAndCarriesBothInteractions() {
        val html = render("普通段落")

        assertTrue(html, html.contains("study-reader-interactivity-script"))
        assertTrue(html, html.contains("studyReaderTasks"))
        assertTrue(html, html.contains("onTaskToggled"))
        assertTrue(html, html.contains("box.removeAttribute(\"disabled\")"))
        assertTrue(html, html.contains("classList.toggle(OPEN)"))
    }

    @Test
    fun sanitizeStillStripsDangerousMarkupAroundAnnotations() {
        val html = render(
            "<script>alert('boom')</script>\n\n==端口<!--typora-annotation-v1-aff473babc935c259c20fd0b-" +
                "56uv5Y+j5piv5pW05pWwLOWPluWAvOiMg+WbtOWcqDAtNjU1MzXkuYvpl7Q-->==说明",
        )

        assertFalse(html, html.contains("alert('boom')"))
        assertFalse(html, html.contains("<iframe"))
        // 同一篇里注释照样要保住
        assertTrue(html, html.contains("data-study-note="))
    }
}
