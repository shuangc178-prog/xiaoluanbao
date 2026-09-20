package com.example.campuslife.ui.study

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.campuslife.data.study.StudyOutlineItem
import com.example.campuslife.data.study.parseMarkdownOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs

@Composable
internal fun StudyDocumentRenderer(
    document: StudyDocumentUiModel,
    textZoom: Int,
    vocabularyTableMaskEnabled: Boolean,
    onWebViewReady: (WebView?) -> Unit,
    onProgressChange: (scrollY: Int, progress: Float) -> Unit,
    onExternalLink: (String) -> Unit,
    onOutlineChange: (List<StudyOutlineItem>) -> Unit = {},
    checkedTaskIndexes: Set<Int> = emptySet(),
    onTaskToggle: (index: Int, checked: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val css = studyReaderCss(studyReaderPalette())
    val currentOnTaskToggle by rememberUpdatedState(onTaskToggle)
    val rendered by produceState<RenderedStudyDocument?>(
        initialValue = null,
        document.id,
        document.snapshotId,
        document.relativePath,
        document.supportsVocabularyTableMask,
        css,
    ) {
        value = withContext(Dispatchers.IO) {
            renderStoredDocument(
                filesDirectory = context.filesDir,
                document = document,
                css = css,
                enableVocabularyTableMask = document.supportsVocabularyTableMask,
            )
        }
    }
    var webView by remember(document.id) { mutableStateOf<WebView?>(null) }
    // 页面里的脚本要等 DOM 就绪才能接状态；在那之前 evaluateJavascript 会打到空对象上，
    // 所以由脚本用同一个桥回一个「就绪」信号，Compose 侧收到后再推一次。
    var isReaderReady by remember(document.id) { mutableStateOf(false) }
    val currentVocabularyTableMaskEnabled by rememberUpdatedState(
        vocabularyTableMaskEnabled && document.supportsVocabularyTableMask,
    )

    LaunchedEffect(rendered?.outline) {
        onOutlineChange(rendered?.outline.orEmpty())
    }

    LaunchedEffect(webView, vocabularyTableMaskEnabled, document.supportsVocabularyTableMask, isReaderReady) {
        if (isReaderReady) webView?.setVocabularyTableMaskEnabled(currentVocabularyTableMaskEnabled)
    }

    // 勾选是「App 侧一份状态 + 正文里已勾上的项」合并出来的，改完要推回页面，
    // 否则点一下会被下一次 recomposition 又画回旧状态。
    LaunchedEffect(webView, checkedTaskIndexes, isReaderReady) {
        if (isReaderReady) webView?.setCheckedTaskIndexes(checkedTaskIndexes)
    }

    DisposableEffect(document.id) {
        onDispose {
            onWebViewReady(null)
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    val content = rendered
    when {
        content == null -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        content?.error != null -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = content?.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> {
            val ready = requireNotNull(content)
            AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { viewContext ->
                    val importsRoot = File(viewContext.filesDir, "study_imports").also(File::mkdirs)
                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler(
                            "/study/",
                            WebViewAssetLoader.InternalStoragePathHandler(viewContext, importsRoot),
                        )
                        .build()
                    WebView(viewContext).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        settings.apply {
                            // 页面内容完全由本端生成，用户 Markdown 已做过 sanitize
                            // （清除 script/iframe/事件处理器/javascript: 链接），
                            // 因此这里开启 JS 仅用于目录锚点、表格遮挡、注释展开与任务勾选，
                            // 不引入任何外部脚本。
                            javaScriptEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            blockNetworkLoads = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            domStorageEnabled = false
                            databaseEnabled = false
                            mediaPlaybackRequiresUserGesture = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            this.textZoom = textZoom
                        }
                        // 勾选回传只走这个接口，且暴露的方法只做一件事：把序号和状态交给
                        // Compose 侧存覆盖层。不反射任何其他对象，避免 JNI 桥被页面滥用。
                        addJavascriptInterface(
                            StudyTaskBridge(
                                onToggle = { index, checked -> currentOnTaskToggle(index, checked) },
                                onReady = { isReaderReady = true },
                            ),
                            StudyTaskBridge.NAME,
                        )
                        webViewClient = LocalDocumentWebViewClient(
                            assetLoader = assetLoader,
                            onExternalLink = onExternalLink,
                        ) {
                            post { scrollTo(0, document.scrollY.coerceAtLeast(0)) }
                        }
                        val readerView = this
                        var lastReportedY = -1
                        var lastReportedProgress = -1f
                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            val range = (readerView.getContentHeight() * readerView.getScale() - readerView.height)
                                .toInt()
                                .coerceAtLeast(1)
                            val progress = (scrollY.toFloat() / range).coerceIn(0f, 1f)
                            if (
                                abs(scrollY - lastReportedY) >= 32 ||
                                abs(progress - lastReportedProgress) >= 0.005f
                            ) {
                                lastReportedY = scrollY
                                lastReportedProgress = progress
                                onProgressChange(scrollY, progress)
                            }
                        }
                        loadDataWithBaseURL(
                            ready.baseUrl,
                            ready.html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        webView = this
                        onWebViewReady(this)
                    }
                },
                update = { view ->
                    view.settings.textZoom = textZoom
                },
            )
        }
    }
}

private class LocalDocumentWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val onExternalLink: (String) -> Unit,
    private val onPageReady: () -> Unit,
) : WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = localOrBlockedResponse(request.url)

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
        localOrBlockedResponse(Uri.parse(url))

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        handleNavigation(request.url)

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(Uri.parse(url))

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady()
    }

    private fun localOrBlockedResponse(uri: Uri): WebResourceResponse? {
        if (uri.scheme == "https" && uri.host == WebViewAssetLoader.DEFAULT_DOMAIN) {
            return assetLoader.shouldInterceptRequest(uri)
        }
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun handleNavigation(uri: Uri): Boolean {
        if (uri.scheme == "https" && uri.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
        if (uri.scheme == "about") return false
        onExternalLink(uri.toString())
        return true
    }
}

private data class RenderedStudyDocument(
    val html: String,
    val baseUrl: String,
    val outline: List<StudyOutlineItem> = emptyList(),
    val error: String? = null,
)

private fun renderStoredDocument(
    filesDirectory: File,
    document: StudyDocumentUiModel,
    css: String,
    enableVocabularyTableMask: Boolean,
): RenderedStudyDocument {
    val importsRoot = File(filesDirectory, "study_imports")
    val snapshotRoot = File(importsRoot, document.snapshotId)
    val source = File(snapshotRoot, document.relativePath)
    val safeSource = runCatching {
        source.canonicalPath.startsWith(snapshotRoot.canonicalPath + File.separator) && source.isFile
    }.getOrDefault(false)
    if (!safeSource) {
        return RenderedStudyDocument("", "", error = "找不到这份资料的本地副本")
    }

    val sourceText = runCatching { source.readText(Charsets.UTF_8).removePrefix("\uFEFF") }
        .getOrElse { error ->
            return RenderedStudyDocument("", "", error = "读取失败：${error.message ?: "编码异常"}")
        }
    val rendered = renderMarkdown(sourceText, css, enableVocabularyTableMask)
    val parentPath = source.parentFile
        ?.relativeToOrNull(importsRoot)
        ?.invariantSeparatorsPath
        .orEmpty()
        .split('/')
        .filter(String::isNotBlank)
        .joinToString("/") { Uri.encode(it) }
    val baseUrl = buildString {
        append("https://")
        append(WebViewAssetLoader.DEFAULT_DOMAIN)
        append("/study/")
        if (parentPath.isNotEmpty()) append(parentPath).append('/')
    }
    return RenderedStudyDocument(
        html = rendered.html,
        baseUrl = baseUrl,
        outline = rendered.outline,
    )
}

internal data class RenderedMarkdown(
    val html: String,
    val outline: List<StudyOutlineItem>,
)

internal fun renderMarkdown(
    source: String,
    css: String,
    enableVocabularyTableMask: Boolean = false,
): RenderedMarkdown {
    val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
    )
    // 顺序是硬要求：Typora 的注释与颜色都是「高亮里塞一段 HTML 注释」，
    // 必须在 sanitize（会清掉 `<!--...-->` 之外的裸标签，也会清掉带 on* 的属性）**之前**
    // 换成安全的 span，否则注释正文会被当成普通 HTML 注释整条丢掉——那正是之前的行为。
    val safeSource = substituteTyporaAnnotations(source)
        .let(::sanitizeSourceMarkup)
        .replace(HIGHLIGHT_MARKER) { match -> "<mark>${match.groupValues[1]}</mark>" }
        .replace(ROOT_RELATIVE_MARKDOWN_LINK) { match -> "${match.groupValues[1]}${match.groupValues[2]}" }
    val outline = parseMarkdownOutline(safeSource)
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .sanitizeUrls(true)
        .build()
    val body = injectHeadingAnchors(renderer.render(parser.parse(safeSource)), outline)
    val vocabularyMaskAssets = if (enableVocabularyTableMask) VOCABULARY_TABLE_MASK_ASSETS else ""
    val html = """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">$css$vocabularyMaskAssets$READER_INTERACTIVITY_ASSETS</head>
        <body>$body</body></html>
    """.trimIndent()
    return RenderedMarkdown(html = html, outline = outline)
}

/**
 * commonmark 渲染出的标题标签是不带 id 的 `<hN>`，这里按 [outline] 的顺序
 * 依次为每个标题注入 `id`，让目录点击后能通过锚点定位到对应正文位置。
 */
private fun injectHeadingAnchors(body: String, outline: List<StudyOutlineItem>): String {
    if (outline.isEmpty()) return body
    var result = body
    var cursor = 0
    for (item in outline) {
        val tag = "<h${item.level}>"
        val index = result.indexOf(tag, cursor)
        if (index < 0) break
        val replacement = "<h${item.level} id=\"${item.anchor}\">"
        result = result.substring(0, index) + replacement + result.substring(index + tag.length)
        cursor = index + replacement.length
    }
    return result
}

private fun sanitizeSourceMarkup(source: String): String = source
    .replace(DANGEROUS_BLOCK, "")
    .replace(DANGEROUS_SINGLE_TAG, "")
    .replace(INLINE_EVENT_HANDLER, "")
    .replace(JAVASCRIPT_URL) { match ->
        "${match.groupValues[1]}=${match.groupValues[2]}#${match.groupValues[2]}"
    }

/**
 * 勾选回传的 JNI 桥。
 *
 * 只暴露一个方法，方法体只把 `(序号, 是否勾上)` 交给 Compose 侧，不暴露 WebView、
 * 不做反射、不读文件；即使页面脚本被注入，能做的也只有「翻一个任务」。
 */
internal class StudyTaskBridge(
    private val onToggle: (index: Int, checked: Boolean) -> Unit,
    private val onReady: () -> Unit,
) {
    @android.webkit.JavascriptInterface
    fun onTaskToggled(index: Int, checked: Boolean) {
        if (index >= 0) onToggle(index, checked)
    }

    /** 页面脚本绑定完成后回一个信号，Compose 侧据此推首次状态。 */
    @android.webkit.JavascriptInterface
    fun onReaderReady() {
        onReady()
    }

    companion object {
        const val NAME = "studyReaderBridge"
    }
}

/**
 * 把 Typora 的注释与颜色标记换成安全的 HTML 片段。
 *
 * 两种标记都长在「高亮」里：
 * - 注释 `==文字<!--typora-annotation-v1-<id>-<base64url 正文>-->==`
 * - 颜色 `==文字<!--typora-color-v1-<id>-<#rrggbb|none>-<none>-->==`
 *
 * 注释正文是用户手写的备注，必须解出来显示（点高亮展开）；颜色取值只在形如
 * `#rrggbb` 时才落成行内样式，其它一律忽略，避免把任意 CSS 引进文档。
 * 单独占一行的注释（外面没有高亮）也一并保住，不会变成看不见的注释节点。
 */
private fun substituteTyporaAnnotations(source: String): String {
    val noteSpans = TYPORA_ANNOTATION_HIGHLIGHT.replace(source) { match ->
        val note = decodeNotePayload(match.groupValues[3])
        if (note.isEmpty()) {
            "<mark>${match.groupValues[1]}</mark>"
        } else {
            // 备注正文再次用 base64url 放进属性里：commonmark 会把内联 HTML 当文本再转义一遍，
            // 直接写中文或引号会被 `&amp;` 二次转义，页面读到的就是坏文本。
            "<span class=\"study-note\">${match.groupValues[1]}</span>" +
                "<span class=\"study-note-text\" data-study-note=\"${encodeNotePayload(note)}\"></span>"
        }
    }
    val colorSpans = TYPORA_COLOR_HIGHLIGHT.replace(noteSpans) { match ->
        val color = match.groupValues[3]
        if (SAFE_COLOR.matches(color)) {
            val style = " style=\"color: $color\""
            "<mark$style>${match.groupValues[1]}</mark>"
        } else {
            "<mark>${match.groupValues[1]}</mark>"
        }
    }
    return TYPORA_ORPHAN_ANNOTATION.replace(colorSpans) { match ->
        // 这个 pattern 只有两个捕获组：1 = 注释 id，2 = base64url 正文。
        val note = decodeNotePayload(match.groupValues[2])
        if (note.isEmpty()) {
            ""
        } else {
            "<span class=\"study-note-orphan\" data-study-note=\"${encodeNotePayload(note)}\"></span>"
        }
    }
}

private fun decodeNotePayload(payload: String): String = runCatching {
    val normalized = payload.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
}.getOrDefault("")

private fun encodeNotePayload(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

private fun WebView.setVocabularyTableMaskEnabled(enabled: Boolean) {
    evaluateJavascript(
        "window.studyVocabularyTableMask && " +
            "window.studyVocabularyTableMask.setEnabled(${enabled.toString()});",
        null,
    )
}

private fun WebView.setCheckedTaskIndexes(indexes: Set<Int>) {
    val encoded = indexes.sorted().joinToString(",")
    evaluateJavascript(
        "window.studyReaderTasks && window.studyReaderTasks.setChecked([$encoded]);",
        null,
    )
}

/**
 * 阅读器的交互脚本：注释展开、任务勾选回传、备注正文落地。
 *
 * 备注正文不直接写进 HTML 属性以外的位置，由脚本读到 `data-study-note` 后再插进 DOM，
 * 这样正文里出现的 `<`、`&` 不会破坏页面结构。
 */
internal val READER_INTERACTIVITY_ASSETS = """
    <script id="study-reader-interactivity-script">
      (() => {
        const NOTE = "data-study-note";
        const OPEN = "study-note-open";
        const CHECKBOX = 'input[type="checkbox"]';
        const DONE_CLASS = "task-list-done";
        const BRIDGE = "studyReaderBridge";
        let checkboxes = [];

        const decodeNote = (encoded) => {
          try {
            const binary = atob(encoded);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return new TextDecoder("utf-8").decode(bytes);
          } catch (error) {
            return "";
          }
        };

        const attachNotes = () => {
          document.querySelectorAll("[" + NOTE + "]").forEach((element) => {
            if (element.dataset.studyNoteBound) return;
            element.dataset.studyNoteBound = "1";
            const text = decodeNote(element.getAttribute(NOTE));
            if (!text) return;
            if (element.classList.contains("study-note")) {
              element.insertAdjacentHTML(
                "afterend",
                '<div class="study-note-text">' + text.replace(/&/g, "&amp;").replace(/</g, "&lt;") + "</div>"
              );
            } else {
              element.textContent = text;
            }
          });
        };

        const applyDone = (box, done) => {
          box.checked = done;
          const item = box.closest("li");
          if (!item) return;
          // commonmark 对紧凑列表不写 task-list-item 类，这里补上，
          // 完成行的底色与删除线才有一致的落点。
          item.classList.add("task-list-item");
          item.classList.toggle(DONE_CLASS, done);
        };

        const indexOfBox = (box) => checkboxes.indexOf(box);

        const setChecked = (indexes) => {
          const done = new Set(indexes || []);
          checkboxes.forEach((box, index) => applyDone(box, done.has(index)));
        };

        const bindTasks = () => {
          checkboxes = Array.from(document.querySelectorAll(CHECKBOX));
          checkboxes.forEach((box, index) => {
            box.disabled = false;
            box.removeAttribute("disabled");
            if (box.dataset.studyTaskBound) return;
            box.dataset.studyTaskBound = "1";
            box.addEventListener("click", (event) => {
              event.preventDefault();
              event.stopPropagation();
              const next = !box.checked;
              applyDone(box, next);
              const bridge = window[BRIDGE];
              if (bridge && typeof bridge.onTaskToggled === "function") {
                bridge.onTaskToggled(indexOfBox(box), next);
              }
            }, true);
          });
          document.querySelectorAll("li." + DONE_CLASS).forEach((item) => {
            const box = item.querySelector(CHECKBOX);
            if (box && !checkboxes.includes(box)) item.classList.remove(DONE_CLASS);
          });
        };

        document.addEventListener("click", (event) => {
          const target = event.target;
          if (!(target instanceof Element)) return;
          const note = target.closest(".study-note");
          if (!note) return;
          const text = note.nextElementSibling;
          if (text && text.classList.contains("study-note-text")) {
            text.classList.toggle(OPEN);
            event.preventDefault();
            event.stopPropagation();
          }
        }, true);

        window.studyReaderTasks = { setChecked };

        const init = () => {
          attachNotes();
          bindTasks();
          const bridge = window[BRIDGE];
          if (bridge && typeof bridge.onReaderReady === "function") bridge.onReaderReady();
        };
        // 脚本在 head 里，正常晚于 DOM 就绪；但 WebView 复用页面时可能已经 readyState=complete，
        // 两条路都要覆盖，否则注释和复选框永远绑不上事件。
        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", init, { once: true });
        } else {
          init();
        }
      })();
    </script>
""".trimIndent()

internal val VOCABULARY_TABLE_MASK_ASSETS = """
    <style id="study-vocabulary-table-mask-style">
      html.study-vocabulary-mask-enabled table tr:first-child > th,
      html.study-vocabulary-mask-enabled table tr:first-child > td { cursor: pointer; }
      table.study-vocabulary-mask-active .study-vocabulary-mask-cell {
        position: relative;
        isolation: isolate;
        overflow: hidden;
        cursor: pointer;
      }
      table.study-vocabulary-mask-active .study-vocabulary-mask-cell::after {
        content: "";
        position: absolute;
        inset: 0;
        z-index: 2147483647;
        display: block;
        /* 与正文底色一致，遮罩看起来才是「空白」而不是一块白纸 */
        background-color: var(--study-canvas, #fff);
        opacity: 1;
      }
      table.study-vocabulary-mask-active .study-vocabulary-mask-cell.study-vocabulary-mask-revealed::after {
        display: none;
      }
    </style>
    <script id="study-vocabulary-table-mask-script">
      (() => {
        const ACTIVE = "study-vocabulary-mask-active";
        const MASKED = "study-vocabulary-mask-cell";
        const REVEALED = "study-vocabulary-mask-revealed";
        const ENABLED = "study-vocabulary-mask-enabled";
        let enabled = false;
        let selectedTable = null;
        let selectedColumn = -1;

        const clearSelection = () => {
          document.querySelectorAll("table." + ACTIVE).forEach((table) => table.classList.remove(ACTIVE));
          document.querySelectorAll("." + MASKED).forEach((cell) => {
            cell.classList.remove(MASKED, REVEALED);
          });
          selectedTable = null;
          selectedColumn = -1;
        };

        const selectColumn = (table, columnIndex) => {
          clearSelection();
          selectedTable = table;
          selectedColumn = columnIndex;
          table.classList.add(ACTIVE);
          Array.from(table.rows).slice(1).forEach((row) => {
            const cell = Array.from(row.cells).find((candidate) => candidate.cellIndex === columnIndex);
            if (cell) cell.classList.add(MASKED);
          });
        };

        window.studyVocabularyTableMask = {
          setEnabled(nextEnabled) {
            enabled = Boolean(nextEnabled);
            clearSelection();
            document.documentElement.classList.toggle(ENABLED, enabled);
          },
        };

        document.addEventListener("click", (event) => {
          if (!enabled || !(event.target instanceof Element)) return;
          const cell = event.target.closest("th, td");
          const table = cell ? cell.closest("table") : null;
          if (!cell || !table) return;

          const rowIndex = Array.from(table.rows).indexOf(cell.parentElement);
          if (rowIndex === 0) {
            selectColumn(table, cell.cellIndex);
          } else if (table === selectedTable && cell.cellIndex === selectedColumn) {
            cell.classList.toggle(REVEALED);
          } else {
            return;
          }

          event.preventDefault();
          event.stopPropagation();
        }, true);
      })();
    </script>
""".trimIndent()

private val DANGEROUS_BLOCK = Regex(
    "(?is)<(script|iframe|object|embed|form|svg|math)[^>]*>.*?</\\1\\s*>",
)
private val DANGEROUS_SINGLE_TAG = Regex("(?is)<(script|iframe|object|embed|form|base|meta)[^>]*?/?>")
private val INLINE_EVENT_HANDLER = Regex("(?is)\\s+on[a-z]+\\s*=\\s*([\"']).*?\\1")
private val JAVASCRIPT_URL = Regex("(?is)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2")
private val HIGHLIGHT_MARKER = Regex("==([^=\\n]+)==")
private val ROOT_RELATIVE_MARKDOWN_LINK = Regex("(!?\\[[^]]*]\\()/(?!/)([^)]+\\))")

/**
 * 高亮里的 Typora 注释。第 3 组是 base64url 正文（没有 `=` 填充），字符集按 URL-safe 取。
 * 正文里不会出现 `-`，所以用 `-` 作分隔符不会误切；结尾的 `-->` 必须带上，避免吃掉后面的内容。
 */
private val TYPORA_ANNOTATION_HIGHLIGHT = Regex(
    "(?is)==([^=\\n]*?)<!--\\s*typora-annotation-v1-([0-9a-f]+)-([A-Za-z0-9+/]*)-*\\s*-->\\s*==",
)
private val TYPORA_COLOR_HIGHLIGHT = Regex(
    "(?is)==([^=\\n]*?)<!--\\s*typora-color-v1-([0-9a-f]+)-([#0-9a-zA-Z]+)-[0-9a-zA-Z]+\\s*-->\\s*==",
)
private val TYPORA_ORPHAN_ANNOTATION = Regex(
    "<!--\\s*typora-annotation-v1-([0-9a-f]+)-([A-Za-z0-9+/]*)-*\\s*-->(?!\\s*==)",
)
private val SAFE_COLOR = Regex("^#[0-9a-fA-F]{3,8}$")
