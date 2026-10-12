package com.example.campuslife.ui.study

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.example.campuslife.ui.components.ClaudeIconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.campuslife.ui.components.ClaudeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.study.StudyOutlineItem
import com.example.campuslife.ui.theme.CampusLifeTheme

@Composable
fun StudyReaderScreen(
    state: StudyReaderUiState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onProgressChange: (scrollY: Int, progress: Float) -> Unit = { _, _ -> },
    onVocabularyTableMaskChange: (String?) -> Unit = {},
    onTaskCheckboxStateChange: (String?) -> Unit = {},
    previewContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var showFind by rememberSaveable(state.document.id) { mutableStateOf(false) }
    var findQuery by rememberSaveable(state.document.id) { mutableStateOf("") }
    var findResultLabel by remember(state.document.id) { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var textZoom by rememberSaveable(state.document.id) { mutableIntStateOf(100) }
    var webView by remember(state.document.id) { mutableStateOf<WebView?>(null) }
    var externalUrl by remember { mutableStateOf<String?>(null) }
    var outline by remember(state.document.id) { mutableStateOf<List<StudyOutlineItem>>(emptyList()) }
    var showOutline by remember(state.document.id) { mutableStateOf(false) }
    // 有已保存的遮挡状态就直接按离开时的样子打开。这个值不另走 rememberSaveable：
    // 资料自己的持久状态才是事实来源，避免退出阅读器后恢复出一个过期的开关值。
    var vocabularyTableMaskEnabled by remember(state.document.id) {
        mutableStateOf(state.document.vocabularyMask != null)
    }
    // 遮挡状态由 WebView 回报并立即落盘；这里只在进入某篇资料时取一次初值，
    // 之后不再跟随落盘回流覆盖，否则用户当前正在翻的单元格会被覆盖。
    var vocabularyTableMask by remember(state.document.id) { mutableStateOf(state.document.vocabularyMask) }
    // 复选框状态同理：进入资料时取一次初值，之后由 WebView 回报并落盘，不跟随回流覆盖。
    var taskCheckboxState by remember(state.document.id) { mutableStateOf(state.document.taskCheckboxState) }

    BackHandler(onBack = onBackClick)

    LaunchedEffect(webView, findQuery) {
        val view = webView ?: return@LaunchedEffect
        if (findQuery.isBlank()) {
            view.clearMatches()
            findResultLabel = ""
        } else {
            view.findAllAsync(findQuery)
        }
    }

    externalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { externalUrl = null },
            title = { Text("打开外部链接？") },
            text = { Text(url) },
            confirmButton = {
                ClaudeTextButton(
                    onClick = {
                        externalUrl = null
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("打开")
                }
            },
            dismissButton = {
                ClaudeTextButton(onClick = { externalUrl = null }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

            Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                ),
        ) {
            ReaderToolbar(
                state = state,
                onBackClick = onBackClick,
                onFindInDocumentClick = { showFind = !showFind },
                onOutlineClick = { showOutline = true },
                showSettings = showSettings,
                onShowSettingsChange = { showSettings = it },
                textZoom = textZoom,
                onTextZoomChange = { textZoom = it },
                vocabularyTableMaskAvailable = state.document.supportsVocabularyTableMask,
                vocabularyTableMaskEnabled = vocabularyTableMaskEnabled,
                onVocabularyTableMaskChange = { vocabularyTableMaskEnabled = it },
            )
            if (showFind) {
                FindInDocumentBar(
                    query = findQuery,
                    resultLabel = findResultLabel,
                    onQueryChange = { findQuery = it },
                    onPrevious = { webView?.findNext(false) },
                    onNext = { webView?.findNext(true) },
                    onClose = {
                        showFind = false
                        findQuery = ""
                        webView?.clearMatches()
                    },
                )
            }
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (previewContent != null) {
                    previewContent()
                } else {
                    StudyDocumentRenderer(
                        document = state.document,
                        textZoom = textZoom,
                        vocabularyTableMaskEnabled = vocabularyTableMaskEnabled,
                        vocabularyTableMask = vocabularyTableMask,
                        onVocabularyTableMaskChange = { reported ->
                            vocabularyTableMask = reported
                            onVocabularyTableMaskChange(reported)
                        },
                        taskCheckboxState = taskCheckboxState,
                        onTaskCheckboxStateChange = { reported ->
                            taskCheckboxState = reported
                            onTaskCheckboxStateChange(reported)
                        },
                        onWebViewReady = { view ->
                            webView = view
                            view?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                if (isDoneCounting) {
                                    findResultLabel = if (numberOfMatches == 0) {
                                        "无匹配"
                                    } else {
                                        "${activeMatchOrdinal + 1} / $numberOfMatches"
                                    }
                                }
                            }
                        },
                        onProgressChange = onProgressChange,
                        onExternalLink = { externalUrl = it },
                        onOutlineChange = { outline = it },
                    )
                }
            }
        }

        if (showOutline) {
            OutlineDrawer(
                outline = outline,
                onItemClick = { anchor ->
                    showOutline = false
                    webView?.evaluateJavascript(
                        "document.getElementById('$anchor').scrollIntoView({behavior:'auto', block:'start'});",
                        null,
                    )
                },
                onDismiss = { showOutline = false },
            )
        }
        }
    }
}

@Composable
private fun ReaderToolbar(
    state: StudyReaderUiState,
    onBackClick: () -> Unit,
    onFindInDocumentClick: () -> Unit,
    onOutlineClick: () -> Unit,
    showSettings: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    textZoom: Int,
    onTextZoomChange: (Int) -> Unit,
    vocabularyTableMaskAvailable: Boolean,
    vocabularyTableMaskEnabled: Boolean,
    onVocabularyTableMaskChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaudeIconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回资料库",
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = state.document.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${state.document.format.shortLabel} · ${state.progressLabel}",
                modifier = Modifier.padding(top = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ClaudeIconButton(onClick = onFindInDocumentClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "在文档中查找",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ClaudeIconButton(onClick = onOutlineClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Toc,
                contentDescription = "目录",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (vocabularyTableMaskAvailable) {
            IconToggleButton(
                checked = vocabularyTableMaskEnabled,
                onCheckedChange = onVocabularyTableMaskChange,
            ) {
                Icon(
                    imageVector = if (vocabularyTableMaskEnabled) {
                        Icons.Outlined.Visibility
                    } else {
                        Icons.Outlined.VisibilityOff
                    },
                    contentDescription = if (vocabularyTableMaskEnabled) {
                        "关闭表格遮挡"
                    } else {
                        "开启表格遮挡"
                    },
                    tint = if (vocabularyTableMaskEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Box {
            ClaudeIconButton(onClick = { onShowSettingsChange(true) }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "更多阅读选项",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = showSettings,
                onDismissRequest = { onShowSettingsChange(false) },
            ) {
                Text(
                    text = "文字大小",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(85 to "小", 100 to "标准", 115 to "大", 130 to "特大").forEach { (zoom, label) ->
                    DropdownMenuItem(
                        text = { Text(if (zoom == textZoom) "$label ✓" else label) },
                        onClick = {
                            onTextZoomChange(zoom)
                            onShowSettingsChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FindInDocumentBar(
    query: String,
    resultLabel: String,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("在文档中查找") },
            singleLine = true,
            trailingIcon = if (resultLabel.isNotEmpty()) {
                { Text(resultLabel, style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
        )
        ClaudeIconButton(onClick = onPrevious, enabled = query.isNotBlank()) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个匹配")
        }
        ClaudeIconButton(onClick = onNext, enabled = query.isNotBlank()) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个匹配")
        }
        ClaudeIconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭查找")
        }
    }
}

@Composable
private fun OutlineDrawer(
    outline: List<StudyOutlineItem>,
    onItemClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .align(Alignment.TopEnd),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 14.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "目录",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ClaudeIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭目录",
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (outline.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "本文没有标题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(outline, key = { it.anchor }) { item ->
                            OutlineRow(item = item, onClick = { onItemClick(item.anchor) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(
    item: StudyOutlineItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indent = (item.level - 1).coerceAtLeast(0) * 14
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + indent).dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = when {
                item.level <= 1 -> MaterialTheme.typography.bodyLarge
                item.level == 2 -> MaterialTheme.typography.bodyMedium
                else -> MaterialTheme.typography.bodySmall
            },
            fontWeight = if (item.level <= 2) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                item.level <= 2 -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ReaderArticlePreview(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = "Python 基础",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "学习笔记 · 最后修改于今天 15:56",
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "这是一段用于展示阅读排版的 Typora 文档预览。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        item {
            Text(
                text = "网络编程",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "客户端与服务端通过网络交换数据。学习时可以先理解一次请求经过的步骤，再回到具体代码中观察每个参数的作用。",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "client.connect((host, port))\nresponse = client.recv(1024)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(
    name = "Reader",
    group = "Study",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun StudyReaderScreenPreview() {
    CampusLifeTheme(darkTheme = false) {
        StudyReaderScreen(
            state = StudyPreviewData.reader,
            previewContent = { ReaderArticlePreview() },
        )
    }
}
