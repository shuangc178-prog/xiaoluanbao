package com.example.campuslife.ui.study

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.example.campuslife.data.study.HASH_CHAR_COUNT
import com.example.campuslife.data.study.StudyOutlineItem
import com.example.campuslife.data.study.parseMarkdownOutline
import com.example.campuslife.data.study.resolveImportedLocalImage
import com.example.campuslife.data.study.taskItemHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Code
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs

@Composable
internal fun StudyDocumentRenderer(
    document: StudyDocumentUiModel,
    textZoom: Int,
    vocabularyTableMaskEnabled: Boolean,
    vocabularyTableMask: String?,
    taskCheckboxState: String? = null,
    onWebViewReady: (WebView?) -> Unit,
    onProgressChange: (scrollY: Int, progress: Float) -> Unit,
    onExternalLink: (String) -> Unit,
    onOutlineChange: (List<StudyOutlineItem>) -> Unit = {},
    onVocabularyTableMaskChange: (String?) -> Unit = {},
    onTaskCheckboxStateChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 遮挡状态不参与 produceState 的键：它每次落盘都会回流到这里，若作为键会让 WebView
    // 重新建页，正在看的段落和已翻开的单元格都会一起丢掉。
    val initialVocabularyTableMask = remember(document.id) {
        vocabularyTableMask?.takeIf { document.supportsVocabularyTableMask }
    }
    // 复选框状态同理：勾选一次就重建一次 WebView 的话，滚动位置和展开状态全都会丢。
    val initialTaskCheckboxState = remember(document.id) { taskCheckboxState }
    val initialVocabularyTableMaskEnabled = vocabularyTableMaskEnabled && document.supportsVocabularyTableMask
    val rendered by produceState<RenderedStudyDocument?>(
        initialValue = null,
        document.id,
        document.snapshotId,
        document.relativePath,
        document.supportsVocabularyTableMask,
    ) {
        value = withContext(Dispatchers.IO) {
            renderStoredDocument(
                filesDirectory = context.filesDir,
                document = document,
                enableVocabularyTableMask = document.supportsVocabularyTableMask,
                vocabularyTableMaskState = initialVocabularyTableMask,
                // 建页时就带上开关状态：页面脚本自己恢复，不依赖 onPageFinished 的先后顺序。
                vocabularyTableMaskOn = initialVocabularyTableMaskEnabled,
                taskCheckboxState = initialTaskCheckboxState,
            )
        }
    }
    var webView by remember(document.id) { mutableStateOf<WebView?>(null) }
    val currentVocabularyTableMaskEnabled by rememberUpdatedState(
        vocabularyTableMaskEnabled && document.supportsVocabularyTableMask,
    )
    val currentOnVocabularyTableMaskChange by rememberUpdatedState(onVocabularyTableMaskChange)
    // 只回报真正的变化，避免开启遮挡开关时的空状态白白写一次 DataStore。
    val lastReportedMask = remember(document.id) { mutableStateOf<String?>(initialVocabularyTableMask) }
    val currentOnTaskCheckboxStateChange by rememberUpdatedState(onTaskCheckboxStateChange)
    val lastReportedTaskState = remember(document.id) { mutableStateOf(initialTaskCheckboxState) }

    LaunchedEffect(rendered?.outline) {
        onOutlineChange(rendered?.outline.orEmpty())
    }

    LaunchedEffect(webView, vocabularyTableMaskEnabled, document.supportsVocabularyTableMask) {
        webView?.setVocabularyTableMaskEnabled(currentVocabularyTableMaskEnabled)
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
                        .addPathHandler(
                            "/assets/",
                            WebViewAssetLoader.AssetsPathHandler(viewContext),
                        )
                        .build()
                    WebView(viewContext).apply {
                        // 阅读画布固定为 Claude Desktop Light，避免 WebView 建页时闪出系统白或 App 暗色。
                        setBackgroundColor(AndroidColor.rgb(250, 249, 245))
                        // 页面内容完全由本端生成，用户 Markdown 已做过 sanitize
                        // （清除 script/iframe/事件处理器/javascript: 链接），
                        // 因此这里开启 JS 仅用于本地 CodeMirror 高亮、注释浮卡、目录锚点
                        // 与表格遮挡。所有脚本随 APK 打包，网络请求仍被完全阻断。
                        addJavascriptInterface(
                            StudyVocabularyTableMaskBridge { reported ->
                                if (reported != lastReportedMask.value) {
                                    lastReportedMask.value = reported
                                    currentOnVocabularyTableMaskChange(reported)
                                }
                            },
                            VOCABULARY_TABLE_MASK_BRIDGE_NAME,
                        )
                        addJavascriptInterface(
                            StudyTaskCheckboxBridge { reported ->
                                if (reported != lastReportedTaskState.value) {
                                    lastReportedTaskState.value = reported
                                    currentOnTaskCheckboxStateChange(reported)
                                }
                            },
                            TASK_CHECKBOX_BRIDGE_NAME,
                        )
                        settings.apply {
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
                        webViewClient = LocalDocumentWebViewClient(
                            assetLoader = assetLoader,
                            onExternalLink = onExternalLink,
                        ) {
                            post {
                                scrollTo(0, document.scrollY.coerceAtLeast(0))
                                setVocabularyTableMaskEnabled(currentVocabularyTableMaskEnabled)
                            }
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
    enableVocabularyTableMask: Boolean,
    vocabularyTableMaskState: String?,
    vocabularyTableMaskOn: Boolean,
    taskCheckboxState: String?,
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
    val rendered = renderMarkdown(
        source = sourceText,
        enableVocabularyTableMask = enableVocabularyTableMask,
        vocabularyTableMaskState = vocabularyTableMaskState,
        vocabularyTableMaskSourceHash = document.contentHash,
        vocabularyTableMaskOn = vocabularyTableMaskOn,        taskCheckboxState = taskCheckboxState,
        taskCheckboxSourceHash = document.contentHash,
        imageUrlResolver = { imageSource ->
            resolveImportedLocalImage(
                snapshotRoot = snapshotRoot,
                markdownFile = source,
                markdownSource = sourceText,
                imageSource = imageSource,
            )?.let { imageFile -> importedResourceUrl(importsRoot, imageFile) }
                ?: imageSource
        },
    )
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
    css: String = "",
    enableVocabularyTableMask: Boolean = false,
    vocabularyTableMaskState: String? = null,
    vocabularyTableMaskSourceHash: String = "",
    vocabularyTableMaskOn: Boolean = false,
    taskCheckboxState: String? = null,
    taskCheckboxSourceHash: String = "",
    imageUrlResolver: (String) -> String = { it },
): RenderedMarkdown {
    val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
    )
    // 注释必须先于通用 HTML 注释清理转换成 App 自己生成的安全标记；注释正文只以
    // base64url 放进 data 属性，页面端再用 textContent 展示，不会执行其中的 HTML。
    val safeSource = substituteTyporaAnnotations(source)
        .let(::sanitizeSourceMarkup)
        .replace(TYPORA_COMMENT, "")
        .replace(HIGHLIGHT_MARKER) { match -> "<mark>${match.groupValues[1]}</mark>" }
        .replace(ROOT_RELATIVE_MARKDOWN_LINK) { match ->
            // 图片的 `/assets/...` 必须保留到资源解析阶段，普通根路径链接仍沿用旧行为。
            if (match.groupValues[1].startsWith('!')) {
                match.value
            } else {
                "${match.groupValues[1]}${match.groupValues[2]}"
            }
        }
    val outline = parseMarkdownOutline(safeSource)
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .sanitizeUrls(true)
        .attributeProviderFactory { TaskMarkerAttributeProvider }
        .build()
    val documentNode = parser.parse(safeSource)
    rewriteImageSources(documentNode, imageUrlResolver)
    val body = injectHeadingAnchors(renderer.render(documentNode), outline)
    val vocabularyMaskAssets = if (enableVocabularyTableMask) {
        vocabularyTableMaskAssets(
            sourceHash = vocabularyTableMaskSourceHash,
            storedState = vocabularyTableMaskState,
            enabled = vocabularyTableMaskOn,
        )
    } else {
        ""
    }
    val taskCheckboxAssets = taskCheckboxAssets(
        sourceHash = taskCheckboxSourceHash,
        storedState = taskCheckboxState,
    )
    val html = """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="$STUDY_READER_ASSET_BASE_URL/claude-desktop-light.css">
            $css
            $vocabularyMaskAssets
            $taskCheckboxAssets
            <script defer src="$STUDY_READER_ASSET_BASE_URL/codemirror/codemirror.min.js"></script>
            <script defer src="$STUDY_READER_ASSET_BASE_URL/codemirror/runmode.min.js"></script>
            <script defer src="$STUDY_READER_ASSET_BASE_URL/codemirror/mode.min.js"></script>
            <script defer src="$STUDY_READER_ASSET_BASE_URL/study-reader.js"></script>
          </head>
          <body><main id="write">$body</main></body>
        </html>
    """.trimIndent()
    return RenderedMarkdown(html = html, outline = outline)
}

private fun importedResourceUrl(importsRoot: File, resource: File): String? {
    val relativePath = resource.relativeToOrNull(importsRoot)
        ?.invariantSeparatorsPath
        ?.split('/')
        ?.filter(String::isNotBlank)
        ?.joinToString("/") { segment -> Uri.encode(segment) }
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/study/$relativePath"
}

private fun rewriteImageSources(node: Node, resolver: (String) -> String) {
    when (node) {
        is Image -> node.destination = resolver(node.destination)
        is HtmlInline -> node.literal = rewriteRawHtmlImageSources(node.literal, resolver)
        is HtmlBlock -> node.literal = rewriteRawHtmlImageSources(node.literal, resolver)
    }

    var child = node.firstChild
    while (child != null) {
        rewriteImageSources(child, resolver)
        child = child.next
    }
}

private fun rewriteRawHtmlImageSources(
    html: String,
    resolver: (String) -> String,
): String = RAW_HTML_IMAGE_SOURCE.replace(html) { match ->
    val quote = match.groupValues[2]
    val source = match.groupValues[3].ifEmpty { match.groupValues[4] }
    val resolved = resolver(source)
    if (resolved == source) {
        match.value
    } else if (quote.isNotEmpty()) {
        "${match.groupValues[1]}$quote$resolved$quote"
    } else {
        "${match.groupValues[1]}$resolved"
    }
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

private fun WebView.setVocabularyTableMaskEnabled(enabled: Boolean) {
    evaluateJavascript(
        "window.studyVocabularyTableMask && " +
            "window.studyVocabularyTableMask.setEnabled(${enabled.toString()});",
        null,
    )
}

/**
 * 遮挡样式、状态与脚本。样式直接内联（必须在建页时就在，否则会闪一下未遮挡的表格），
 * 脚本与复选框一样放 assets 里由 WebView 通过 assetLoader 取：Kotlin 原始字符串会把
 * `$名称` / `${...}` 当成自己的插值先吃掉，写在字符串常量里迟早会写出与页面不一致的脚本。
 *
 * [storedState] 是用户上次离开这篇资料时的遮挡状态，由表格和列的位置还原，并按
 * [sourceHash] 校验资料内容没有变过；页面里每次遮挡变化都会回传到 Kotlin 侧落盘，
 * 退出笔记再打开就还是用户翻开的那些单元格。
 */
private fun vocabularyTableMaskAssets(
    sourceHash: String,
    storedState: String?,
    enabled: Boolean,
): String = VOCABULARY_TABLE_MASK_ASSETS
    .replace(
        MASK_INITIAL_STATE_PLACEHOLDER,
        "{\"bridge\":${encodeJsonString(VOCABULARY_TABLE_MASK_BRIDGE_NAME)}," +
            "\"enabled\":${enabled.toString()}," +
            "\"sourceHash\":${encodeJsonString(sourceHash)}," +
            "\"storedState\":${encodeJsonString(storedState)}}"
            .replace("<", "\\u003c"),
    )

/**
 * 复选框脚本与样式随资料一起注入。[storedState] 是用户上次离开这篇资料时的勾选状态，
 * 页面按“表格与行位置 + 该行文字哈希”逐条恢复，并按 [sourceHash] 校验资料没有被换过；
 * 每次勾选都会合并后回传到 Kotlin 侧落盘，资料的 Markdown 快照始终只读。
 *
 * 页面脚本放在 assets 里而不是本文件的字符串常量里：它同时被单元测试直接读取校验，
 * 也避免在 Kotlin 里把整段 JavaScript 写成转义字符串。状态传递仍走
 * `<script type="application/json">`，只转义 `<`，不会被资料内容影响。
 */
/**
 * 把 Typora 表格行任务升格成可点的任务单元格。
 *
 * 判定口径与 Typora 的表格任务扩展严格一致：单元格文字 trim 后完全等于 `[ ]` 或
 * `[x]`/`[X]` 才算任务，`[ ] 买牛奶` 这种带内容的单元格一律按普通文字渲染——否则
 * 会把用户真正写下的内容吃掉。升级后的单元格只多一个文字哈希属性，勾选与否由页面
 * 脚本按用户状态决定，Markdown 源保持原样。
 *
 * 这里走属性提供器而不是自定义渲染器：单元格的其余渲染（对齐、行内节点）仍由
 * commonmark 自己完成，只往命中任务的单元格上追加属性，回归风险最小。
 */
private object TaskMarkerAttributeProvider : AttributeProvider {
    override fun setAttributes(node: Node, tagName: String, attributes: MutableMap<String, String>) {
        when {
            tagName == "td" && node is TableCell -> decorateTableTaskCell(node, attributes)
            tagName == "input" && node is TaskListItemMarker -> decorateListTaskInput(node, attributes)
            tagName == "li" && node is ListItem -> decorateListTaskItem(node, attributes)
        }
    }

    private fun decorateTableTaskCell(
        cell: TableCell,
        attributes: MutableMap<String, String>,
    ) {
        val marker = nodeTaskMarker(cell) ?: return
        if (!isTyporaTableTaskCell(cell, marker)) return
        val checked = marker.trim().equals("[x]", ignoreCase = true)
        addCssClass(attributes, TASK_CELL_CLASS)
        attributes[TASK_HASH_ATTRIBUTE] = taskItemHash(tableTaskRowText(cell))
        attributes[TASK_SOURCE_CHECKED_ATTRIBUTE] = checked.toString()
        attributes["aria-checked"] = checked.toString()
        attributes["aria-label"] = "切换该行任务状态"
        attributes["role"] = "checkbox"
        attributes["tabindex"] = "0"
    }

    private fun decorateListTaskInput(
        marker: TaskListItemMarker,
        attributes: MutableMap<String, String>,
    ) {
        addCssClass(attributes, TASK_LIST_CHECKBOX_CLASS)
        attributes.remove("disabled")
        attributes[TASK_HASH_ATTRIBUTE] = taskItemHash(listTaskItemText(marker))
        attributes[TASK_SOURCE_CHECKED_ATTRIBUTE] = marker.isChecked.toString()
        attributes["aria-label"] = "切换该条任务状态"
    }

    private fun decorateListTaskItem(
        item: ListItem,
        attributes: MutableMap<String, String>,
    ) {
        val marker = item.firstChild as? TaskListItemMarker ?: return
        addCssClass(attributes, TASK_LIST_ITEM_CLASS)
        if (marker.isChecked) addCssClass(attributes, TASK_DONE_CLASS)
    }
}

/** 只复现 Typora 扩展定义的表格任务：首列表头为“完成”，任务格也必须位于首列。 */
private fun isTyporaTableTaskCell(cell: TableCell, marker: String): Boolean {
    if (cell.isHeader || !isTaskMarkerText(marker)) return false
    val row = cell.parent as? TableRow ?: return false
    if (row.firstChild !== cell) return false
    val body = row.parent as? TableBody ?: return false
    val table = body.parent as? TableBlock ?: return false
    val head = table.firstChild as? TableHead ?: return false
    val headerRow = head.firstChild as? TableRow ?: return false
    val headerCell = headerRow.firstChild as? TableCell ?: return false
    return nodeVisibleText(headerCell).trim() == TASK_TABLE_HEADER
}

/** 表格任务用同一行的正文做哈希，不能让每一行都只得到 `[ ]` 这个相同哈希。 */
private fun tableTaskRowText(taskCell: TableCell): String {
    val values = mutableListOf<String>()
    var sibling = taskCell.next
    while (sibling != null) {
        if (sibling is TableCell) values += nodeVisibleText(sibling)
        sibling = sibling.next
    }
    return values.joinToString(TASK_TEXT_SEPARATOR)
}

/** TaskListItemsExtension 会把 marker 放在 ListItem 的第一个孩子，正文紧随其后。 */
private fun listTaskItemText(marker: TaskListItemMarker): String = marker.next
    ?.let(::nodeVisibleText)
    .orEmpty()

private fun nodeVisibleText(root: Node): String {
    val text = StringBuilder()
    fun appendChildren(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is Text -> text.append(child.literal)
                is Code -> text.append(child.literal)
                else -> appendChildren(child)
            }
            child = child.next
        }
    }
    appendChildren(root)
    return text.toString()
}

private fun addCssClass(attributes: MutableMap<String, String>, className: String) {
    val classes = attributes[TASK_CELL_CLASS_ATTRIBUTE]
        .orEmpty()
        .split(' ')
        .filter(String::isNotBlank)
        .toMutableSet()
    classes += className
    attributes[TASK_CELL_CLASS_ATTRIBUTE] = classes.joinToString(" ")
}

/**
 * 单元格可供识别为任务标记的文字。单元格里除了纯文字还有别的行内节点（加粗、链接、
 * 行内代码等）时返回 null——它不是一条任务标记。
 */
private fun nodeTaskMarker(node: TableCell): String? {
    val text = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        if (child is Text) {
            text.append(child.literal)
        } else if (child !is HtmlInline) {
            return null
        }
        child = child.next
    }
    return text.toString()
}

private fun isTaskMarkerText(text: String): Boolean = when (text.trim()) {
    "[ ]", "[x]", "[X]" -> true
    else -> false
}

private const val TASK_CELL_CLASS = "xlb-task-cell"
private const val TASK_LIST_CHECKBOX_CLASS = "xlb-task-list-checkbox"
private const val TASK_LIST_ITEM_CLASS = "xlb-task-list-item"
private const val TASK_DONE_CLASS = "xlb-task-done"
private const val TASK_CELL_CLASS_ATTRIBUTE = "class"
private const val TASK_HASH_ATTRIBUTE = "data-xlb-task-hash"
private const val TASK_SOURCE_CHECKED_ATTRIBUTE = "data-xlb-task-source-checked"
private const val TASK_TABLE_HEADER = "完成"
private const val TASK_TEXT_SEPARATOR = "\u001F"

private fun taskCheckboxAssets(
    sourceHash: String,
    storedState: String?,
): String = TASK_CHECKBOX_ASSETS
    .replace(
        TASK_CHECKBOX_STATE_PLACEHOLDER,
        "{\"sourceHash\":${encodeJsonString(sourceHash.take(HASH_CHAR_COUNT))}," +
            "\"storedState\":${encodeJsonString(storedState)}}"
            .replace("<", "\\u003c"),
    )

/** WebView 把勾选状态交回 Kotlin 的唯一入口；只在真实变化时被调用。 */
private class StudyTaskCheckboxBridge(
    private val onStateChanged: (String?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onTaskStateChanged(encoded: String?) {
        val normalized = encoded?.takeIf(String::isNotBlank)
        mainHandler.post { onStateChanged(normalized) }
    }
}

private fun encodeJsonString(value: String?): String {
    if (value == null) return "null"
    val builder = StringBuilder(value.length + 2)
    builder.append('"')
    value.forEach { character ->
        when (character) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            // U+2028 / U+2029 在 JS 里是换行符，会截断字符串字面量。
            '\u2028', '\u2029' -> builder.append("\\u%04x".format(character.code))
            else -> if (character < ' ') {
                builder.append("\\u%04x".format(character.code))
            } else {
                builder.append(character)
            }
        }
    }
    builder.append('"')
    return builder.toString()
}

/** WebView 把遮挡状态交回 Kotlin 的唯一入口；只在真实变化时被调用。 */
private class StudyVocabularyTableMaskBridge(
    private val onStateChanged: (String?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onMaskStateChanged(encoded: String?) {
        val normalized = encoded?.takeIf(String::isNotBlank)
        mainHandler.post { onStateChanged(normalized) }
    }
}

private const val MASK_INITIAL_STATE_PLACEHOLDER = "__STUDY_MASK_INITIAL_STATE__"
private const val VOCABULARY_TABLE_MASK_BRIDGE_NAME = "xlbStudyVocabularyMask"
private const val TASK_CHECKBOX_BRIDGE_NAME = "xlbStudyTaskCheckbox"
private const val TASK_CHECKBOX_STATE_PLACEHOLDER = "__STUDY_TASK_CHECKBOX_STATE__"

/**
 * 样式和脚本必须让 WebViewAssetLoader 从 APK assets 读取。Android 真机并不存在
 * `src/main/assets/...` 这类源码目录，不能在运行时用 java.io.File 探测它们。
 */
private val TASK_CHECKBOX_ASSETS = """
    <link rel="stylesheet" id="study-task-checkbox-style" href="$STUDY_READER_ASSET_BASE_URL/task-checkbox.css">
    <script type="application/json" id="study-task-checkbox-initial-state">$TASK_CHECKBOX_STATE_PLACEHOLDER</script>
    <script defer data-study-task-bridge="$TASK_CHECKBOX_BRIDGE_NAME" src="$STUDY_READER_ASSET_BASE_URL/task-checkbox.js"></script>
""".trimIndent()

internal val VOCABULARY_TABLE_MASK_ASSETS = """
    <script type="application/json" id="study-vocabulary-mask-initial-state">$MASK_INITIAL_STATE_PLACEHOLDER</script>
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
        background-color: var(--claude-canvas, #faf9f5);
        opacity: 1;
      }
      table.study-vocabulary-mask-active .study-vocabulary-mask-cell.study-vocabulary-mask-revealed::after {
        display: none;
      }
    </style>
    <script defer data-study-mask-bridge="$VOCABULARY_TABLE_MASK_BRIDGE_NAME" src="$STUDY_READER_ASSET_BASE_URL/vocabulary-table-mask.js"></script>
""".trimIndent()

private val DANGEROUS_BLOCK = Regex(
    "(?is)<(script|iframe|object|embed|form|svg|math)[^>]*>.*?</\\1\\s*>",
)
private val DANGEROUS_SINGLE_TAG = Regex("(?is)<(script|iframe|object|embed|form|base|meta)[^>]*?/?>")
private val INLINE_EVENT_HANDLER = Regex("(?is)\\s+on[a-z]+\\s*=\\s*([\"']).*?\\1")
private val JAVASCRIPT_URL = Regex("(?is)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2")
private val TYPORA_COMMENT = Regex("(?is)<!--\\s*typora-(?:color|annotation)-v1.*?-->")
private val HIGHLIGHT_MARKER = Regex("==([^=\\n]+)==")
private val ROOT_RELATIVE_MARKDOWN_LINK = Regex("(!?\\[[^]]*]\\()/(?!/)([^)]+\\))")
private val RAW_HTML_IMAGE_SOURCE = Regex(
    """(?is)(<img\b[^>]*?\s+src\s*=\s*)(?:([\"'])(.*?)\2|([^\s\"'=<>`]+))""",
)

private const val STUDY_READER_ASSET_BASE_URL =
    "https://appassets.androidplatform.net/assets/study_reader"
