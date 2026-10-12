package com.example.campuslife.ui.study

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReaderAssetsTest {

    @Test
    fun claudeLightCssPinsTheTyporaThemeContract() {
        val css = assetText("study_reader/claude-desktop-light.css")

        assertTrue(css.contains("--claude-canvas: #faf9f5"))
        assertTrue(css.contains("--claude-panel: #ffffff"))
        assertTrue(css.contains("--claude-text: #141413"))
        assertTrue(css.contains("--claude-accent: #c6613f"))
        assertTrue(css.contains("--claude-border: rgba(20, 20, 19, 0.12)"))
        assertTrue(css.contains("--claude-content-width: 790px"))
        assertTrue(css.contains("font-family: var(--claude-prose-font)"))
        assertTrue(css.contains("border-collapse: separate"))
        assertTrue(css.contains("background: var(--claude-code-bg)"))
        assertTrue(css.contains(".cm-s-inner .cm-keyword"))
        assertTrue(css.contains("#study-annotation-card"))
    }

    @Test
    fun readerScriptUsesCodeMirrorRunModeAndTextOnlyAnnotations() {
        val script = assetText("study_reader/study-reader.js")

        assertTrue(script.contains("codeMirror.runMode"))
        assertTrue(script.contains("startsWith(\"language-\")"))
        assertTrue(script.contains("data-annotation"))
        assertTrue(script.contains("card.textContent = text"))
        assertTrue(script.contains("event.key === \"Escape\""))
        assertTrue(script.contains("study-image-missing"))
        assertTrue(script.contains("image.addEventListener(\"error\""))
    }

    @Test
    fun taskCheckboxScriptTogglesCellsAndReportsThemBackToKotlin() {
        val script = assetText("study_reader/task-checkbox.js")
        val style = assetText("study_reader/task-checkbox.css")

        assertTrue(script.contains("xlb-task-cell"))
        assertTrue(script.contains("data-xlb-task-hash"))
        assertTrue(script.contains("xlb-task-list-checkbox"))
        assertTrue(script.contains("onTaskStateChanged"))
        assertTrue(script.contains("JSON.parse"))
        assertTrue(script.contains("FIELD_SEPARATOR = \";\""))
        assertTrue(script.contains("Array.prototype.indexOf.call(table.rows, row)"))
        assertTrue(script.contains("lines[0] !== STATE_VERSION || lines[1] !== SOURCE_HASH"))
        assertTrue(script.contains("report();"))
        assertTrue(script.contains("document.addEventListener(\"change\""))
        assertFalse(script.contains("setTimeout"))
        assertFalse(script.contains("clearTimeout"))
        assertFalse(script.contains("split(\"|\")"))
        assertFalse(script.contains("join(\"|\")"))
        // 桥接名从 script 标签的 data 属性读取，脚本仍由 assets 提供、不内联进页面。
        assertTrue(script.contains("data-study-task-bridge"))
        // 必须保留只有状态头的空集合，才能记住用户取消了 Markdown 原有的 `[x]`。
        assertTrue(script.contains("[STATE_VERSION, SOURCE_HASH].concat(marks).join(\"\\n\")"))
        // 表格行任务用 CSS 画方块：勾选后填充主题强调色并显示白色对勾。
        assertTrue(style.contains("background-color: var(--claude-accent, #c6613f)"))
        assertTrue(style.contains("stroke='%23fff'"))
        assertTrue(style.contains("line-through"))
        assertTrue(style.contains("xlb-task-done"))
        assertTrue(style.contains("td.xlb-task-cell[aria-checked=\"true\"]"))
        assertTrue(style.contains("li.xlb-task-list-item"))
        assertTrue(style.contains("input.xlb-task-list-checkbox"))
        assertTrue(style.contains("pointer-events: auto"))
    }

    @Test
    fun codeMirrorRuntimeAndModesAreBundledForOfflineUse() {
        assertTrue(assetFile("study_reader/codemirror/codemirror.min.js").length() > 100_000L)
        assertTrue(assetFile("study_reader/codemirror/runmode.min.js").length() > 500L)
        assertTrue(assetFile("study_reader/codemirror/mode.min.js").length() > 50_000L)
        assertTrue(assetText("study_reader/codemirror/LICENSE.txt").contains("MIT License"))
    }

    private fun assetText(relativePath: String): String = assetFile(relativePath).readText(Charsets.UTF_8)

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/assets", relativePath),
            File("app/src/main/assets", relativePath),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("找不到阅读器资源：$relativePath；检查路径：${candidates.joinToString()}")
    }
}
