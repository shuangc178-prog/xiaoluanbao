package com.example.campuslife.ui.study

import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat
import com.example.campuslife.data.study.taskItemHash
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
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
        val script = maskScriptAsset()

        assertTrue(html.contains("study-vocabulary-table-mask-style"))
        assertTrue(html.contains("vocabulary-table-mask.js"))
        assertTrue(html.contains("inset: 0"))
        assertTrue(html.contains("background-color: var(--claude-canvas, #faf9f5)"))
        assertTrue(script.contains("cell.classList.toggle(REVEALED)"))
        // 恢复与回报必须用同一套行号，否则翻开的单元格下次会被算到别的行。
        assertTrue(script.contains("if (revealedRows.has(rows.indexOf(row))) cell.classList.add(REVEALED);"))
        assertTrue(script.contains("Array.prototype.slice.call(selectedTable.rows)"))
        assertTrue(script.contains("cell.classList.contains(REVEALED)"))
        // 浏览器会在 tr 外插入 thead/tbody；按固定两层 parentElement 查 table 会漏掉所有翻开格。
        assertFalse(script.contains("cell.parentElement.parentElement === selectedTable"))
    }

    @Test
    fun vocabularyModeCarriesTheStoredMaskAndReportsChangesBack() {
        val html = vocabularyHtml(
            maskState = "0:1:hash123:2",
            sourceHash = "hash123",
            enabled = true,
        )
        val script = maskScriptAsset()

        assertEquals(
            """{"bridge":"xlbStudyVocabularyMask","enabled":true,""" +
                """"sourceHash":"hash123","storedState":"0:1:hash123:2"}""",
            initialMaskStateJson(html),
        )
        assertTrue(script.contains("restoreSelection(STORED_STATE)"))
        assertTrue(script.contains("BRIDGE.onMaskStateChanged(encoded)"))
        assertTrue(script.contains("var BRIDGE = initialState.bridge ? window[initialState.bridge] : null;"))
        assertTrue(script.contains("STORED_STATE = encoded;"))
        assertTrue(script.contains("if (!enabled && persistDisabled)"))
        // 页面自己恢复：不依赖 Kotlin 侧 onPageFinished 先跑到。
        assertTrue(script.contains("applyEnabled(hadPendingEnabled ? pendingEnabled : enabled, hadPendingEnabled);"))
    }

    /** 页面脚本随 APK 打包在 assets 里，这里直接读那份文件，测的就是真正跑的东西。 */
    @Test
    fun vocabularyMaskAssetAvoidsKotlinStringTemplateTraps() {
        val script = maskScriptAsset()

        // Kotlin 原始字符串会把 `$名称` 当成自己的插值先吃掉。这个坑真实发生过：
        // 桥接对象名被吃成乱码，脚本再也回报不了遮挡状态，"隐藏一列"于是永远存不下来。
        // 脚本放 assets 里就是为了不可能出现这种改写。
        assertFalse(
            "遮挡脚本里出现 Kotlin 插值写法，说明它又被挪回 Kotlin 字符串里了",
            Regex("\\$[A-Za-z_{]").containsMatchIn(script),
        )
        assertTrue(script.contains("onMaskStateChanged"))
    }

    @Test
    fun vocabularyModeWithoutStoredMaskRendersAnEmptyState() {
        val html = vocabularyHtml(sourceHash = "hash123")

        assertEquals(
            """{"bridge":"xlbStudyVocabularyMask","enabled":false,""" +
                """"sourceHash":"hash123","storedState":null}""",
            initialMaskStateJson(html),
        )
    }

    @Test
    fun maskInitialStateCannotCloseTheScriptBlockEarly() {
        val html = renderMarkdown(
            source = tableMarkdown,
            enableVocabularyTableMask = true,
            vocabularyTableMaskState = "0:1:hash</script><script>alert(1)</script>:2",
            vocabularyTableMaskSourceHash = "hash",
        ).html

        assertFalse(html.contains("</script><script>alert(1)"))
        assertTrue(html.contains("\\u003c/script>"))
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

    @Test
    fun tableTaskMarkersBecomeClickableCellsWithTheirOwnTextHash() {
        val html = renderMarkdown(
            source = """
                | 完成 | 英文 | 中文 |
                | --- | --- | --- |
                | [ ] | appeal | 呼吁 |
                | [x] | cultivate | 培养 |
            """.trimIndent(),
            taskCheckboxSourceHash = "abcdef123456",
        ).html

        assertTrue(html.contains("class=\"xlb-task-cell\""))
        assertTrue(html.contains("data-xlb-task-hash=\"${taskItemHash("appeal\u001F呼吁")}\""))
        assertTrue(html.contains("data-xlb-task-hash=\"${taskItemHash("cultivate\u001F培养")}\""))
        assertTrue(html.contains("data-xlb-task-source-checked=\"false\""))
        assertTrue(html.contains("data-xlb-task-source-checked=\"true\""))
        assertTrue(html.contains("aria-checked=\"false\""))
        assertTrue(html.contains("aria-checked=\"true\""))
        assertTrue(html.contains("study-task-checkbox-initial-state"))
        assertTrue(html.contains("study_reader/task-checkbox.css"))
        assertTrue(html.contains("study_reader/task-checkbox.js"))
        // 标记文字仍然留在单元格里：勾选状态由脚本体现在 class 上，Kotlin 不复制一份可见文字。
        assertTrue(html.contains(">[ ]</td>"))
        // 表头里的“完成”是列名，不是一条任务。
        assertFalse(html.contains("<th class=\"xlb-task-cell\""))
    }

    @Test
    fun tableTaskMarkersRequireTyporasCompletionHeaderInTheFirstColumn() {
        val html = renderMarkdown(
            source = """
                | 计划 | 备注 |
                | --- | --- |
                | [ ] | 买牛奶 |
            """.trimIndent(),
        ).html

        assertFalse(html.contains("<td class=\"xlb-task-cell\""))
        assertFalse(html.contains("data-xlb-task-hash"))
        assertTrue(html.contains(">[ ]</td>"))
    }

    @Test
    fun standaloneMarkdownTasksBecomeClickableAndKeepTheirSourceState() {
        val html = renderMarkdown(
            source = """
                - [ ] 单独待办
                - [x] 已完成
            """.trimIndent(),
            taskCheckboxSourceHash = "abcdef123456",
        ).html

        assertTrue(html.contains("xlb-task-list-item"))
        assertTrue(html.contains("xlb-task-list-checkbox"))
        assertTrue(html.contains("data-xlb-task-hash=\"${taskItemHash("单独待办")}\""))
        assertTrue(html.contains("data-xlb-task-hash=\"${taskItemHash("已完成")}\""))
        assertTrue(html.contains("data-xlb-task-source-checked=\"false\""))
        assertTrue(html.contains("data-xlb-task-source-checked=\"true\""))
        assertTrue(html.contains("xlb-task-done"))
        assertFalse(html.contains("disabled=\"\""))
    }

    @Test
    fun cellsWithRealContentStayPlainText() {
        val html = renderMarkdown(
            source = """
                | 计划 | 备注 |
                | --- | --- |
                | [ ] 买牛奶 | 顺路 |
                | 已经做过 | 无 |
            """.trimIndent(),
        ).html

        // 注意断言的是标签本身：注入的样式与脚本里也有 `xlb-task-cell` 这个类名。
        assertFalse(html.contains("<td class=\"xlb-task-cell\""))
        assertFalse(html.contains("data-xlb-task-hash"))
        assertTrue(html.contains("[ ] 买牛奶"))
    }

    @Test
    fun storedTaskCheckboxStateIsHandedToThePageWithTheBridgeName() {
        val html = renderMarkdown(
            source = "| 完成 |\n| --- |\n| [ ] |",
            taskCheckboxState = "v1\nabcdef123456\n0;1;0;9f3a1c229f3a",
            taskCheckboxSourceHash = "abcdef1234567890",
        ).html

        assertTrue(
            html.contains(
                "{\"sourceHash\":\"abcdef123456\",\"storedState\":\"v1\\nabcdef123456\\n0;1;0;9f3a1c229f3a\"}",
            ),
        )
        assertTrue(html.contains("study-task-checkbox-style"))
        assertTrue(html.contains("study_reader/task-checkbox.css"))
        assertTrue(html.contains("data-study-task-bridge=\"xlbStudyTaskCheckbox\""))
        assertFalse(html.contains("__STUDY_TASK_CHECKBOX_STATE__"))
    }

    @Test
    fun taskCheckboxStateCannotCloseTheScriptBlockEarly() {
        val html = renderMarkdown(
            source = "| 完成 |\n| --- |\n| [ ] |",
            taskCheckboxState = "v1\nhash</script><script>alert(1)</script>\n0;1;0;9f3a1c229f3a",
            taskCheckboxSourceHash = "hash",
        ).html

        assertFalse(html.contains("</script><script>alert(1)"))
        assertTrue(html.contains("\\u003c/script>"))
    }

    @Test
    fun renderedDocumentUsesClaudeThemeAndOfflineCodeMirrorAssets() {
        val html = renderMarkdown(
            source = """
                # 标题

                ```python
                def greet(name):
                    return f"hello {name}"
                ```
            """.trimIndent(),
        ).html

        assertTrue(html.contains("<main id=\"write\">"))
        assertTrue(html.contains("study_reader/claude-desktop-light.css"))
        assertTrue(html.contains("study_reader/codemirror/codemirror.min.js"))
        assertTrue(html.contains("study_reader/codemirror/runmode.min.js"))
        assertTrue(html.contains("study_reader/codemirror/mode.min.js"))
        assertTrue(html.contains("study_reader/study-reader.js"))
        assertTrue(html.contains("class=\"language-python\""))
        assertFalse(html.contains("http://cdnjs"))
        assertFalse(html.contains("https://cdn"))
    }

    @Test
    fun localImageResolverCoversMarkdownAndRawHtmlImages() {
        val html = renderMarkdown(
            source = """
                ![网络图](assets/network.png)

                <img src="/shared/python.png" alt="Python">
            """.trimIndent(),
            imageUrlResolver = { source ->
                "https://appassets.androidplatform.net/study/snapshot/" +
                    source.trimStart('/').replace(" ", "%20")
            },
        ).html

        assertTrue(
            html.contains(
                "src=\"https://appassets.androidplatform.net/study/snapshot/assets/network.png\"",
            ),
        )
        assertTrue(
            html.contains(
                "src=\"https://appassets.androidplatform.net/study/snapshot/shared/python.png\"",
            ),
        )
    }

    @Test
    fun typoraAnnotationKeepsVisibleTextAndSafeUtf8Payload() {
        val html = renderMarkdown(
            source = "==端口<!--typora-annotation-v1-aff473babc935c259c20fd0b-" +
                "56uv5Y+j5piv5pW05pWwLOWPluWAvOiMg+WbtOWcqDAtNjU1MzXkuYvpl7Q-->==是入口",
        ).html

        assertTrue(html.contains("class=\"typora-annotation\""))
        assertTrue(html.contains(">端口</mark>是入口"))
        assertFalse(html.contains("typora-annotation-v1"))

        val encoded = Regex("data-annotation=\"([^\"]+)\"").find(html)!!.groupValues[1]
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        val note = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        assertEquals("端口是整数,取值范围在0-65535之间", note)
    }

    @Test
    fun legacyExternalAnnotationAlsoBindsToItsHighlight() {
        val note = Base64.getEncoder().withoutPadding()
            .encodeToString("旧格式注释".toByteArray(Charsets.UTF_8))
        val html = renderMarkdown(
            source = "==旧笔记==<!--typora-annotation:v1:abcdef:$note-->",
        ).html

        assertTrue(html.contains("class=\"typora-annotation\""))
        assertTrue(html.contains(">旧笔记</mark>"))
        assertFalse(html.contains("typora-annotation:v1"))
    }

    @Test
    fun invalidAnnotationPayloadFallsBackToPassiveHighlight() {
        val html = renderMarkdown(
            source = "==仍然可见<!--typora-annotation-v1-abcdef-not_base64%-->==",
        ).html

        assertTrue(html.contains("<mark>仍然可见</mark>"))
        assertFalse(html.contains("class=\"typora-annotation\""))
        assertFalse(html.contains("typora-annotation-v1"))
    }

    @Test
    fun dangerousMarkupIsStillRemovedAroundAnnotations() {
        val note = Base64.getEncoder().withoutPadding()
            .encodeToString("安全注释".toByteArray(Charsets.UTF_8))
        val html = renderMarkdown(
            source = "<script>alert('boom')</script>\n\n==正文<!--typora-annotation-v1-abcdef-$note-->==",
        ).html

        assertFalse(html.contains("alert('boom')"))
        assertTrue(html.contains("class=\"typora-annotation\""))
    }

    private fun vocabularyHtml(
        maskState: String? = null,
        sourceHash: String = "",
        enabled: Boolean = false,
    ): String = renderMarkdown(
        source = tableMarkdown,
        css = "<style></style>",
        enableVocabularyTableMask = true,
        vocabularyTableMaskState = maskState,
        vocabularyTableMaskSourceHash = sourceHash,
        vocabularyTableMaskOn = enabled,
    ).html

    /** 取出页面里那块初始化 JSON，按解析结果断言，避免依赖字符串顺序。 */
    private fun initialMaskStateJson(html: String): String = html
        .substringAfter("<script type=\"application/json\" id=\"study-vocabulary-mask-initial-state\">")
        .substringBefore("</script>")

    private fun maskScriptAsset(): String = maskAssetFile("vocabulary-table-mask.js")
        .readText(Charsets.UTF_8)

    private fun maskAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/study_reader", name),
            File("app/src/main/assets/study_reader", name),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("找不到阅读器资源：$name；检查路径：${candidates.joinToString()}")
    }
}
