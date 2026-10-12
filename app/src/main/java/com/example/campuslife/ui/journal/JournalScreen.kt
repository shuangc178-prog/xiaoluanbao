package com.example.campuslife.ui.journal

import com.example.campuslife.ui.theme.pressBounce
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import com.example.campuslife.ui.components.ClaudeButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.campuslife.ui.components.ClaudeFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.example.campuslife.ui.components.ClaudeIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.campuslife.ui.components.ClaudeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.theme.claude
import kotlinx.coroutines.delay
import java.util.UUID

private data class JournalEditorTarget(
    val entryId: String?,
    val categoryId: String,
    val categoryName: String,
    val initialContent: String,
    val dateLabel: String,
)

private data class CategoryNameTarget(
    val categoryId: String?,
    val initialName: String,
)

@Composable
fun JournalScreen(
    state: JournalUiState,
    modifier: Modifier = Modifier,
    onEntryClick: (String) -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onCreateCategory: (String) -> Unit = {},
    onRenameCategory: (id: String, name: String) -> Unit = { _, _ -> },
    onDeleteCategory: (String) -> Unit = {},
    onSaveEntry: (id: String?, content: String, categoryId: String) -> Unit = { _, _, _ -> },
    onToggleAutoSave: (Boolean) -> Unit = {},
    onDeleteEntry: (String) -> Unit = {},
) {
    var editorTarget by remember { mutableStateOf<JournalEditorTarget?>(null) }
    var categoryNameTarget by remember { mutableStateOf<CategoryNameTarget?>(null) }
    var deletingCategory by remember { mutableStateOf<JournalCategoryUiModel?>(null) }

    val target = editorTarget
    if (target != null) {
        JournalEditorScreen(
            target = target,
            autoSaveEnabled = state.autoSaveEnabled,
            modifier = modifier,
            onBack = { editorTarget = null },
            onSave = { content ->
                onSaveEntry(target.entryId, content, target.categoryId)
                editorTarget = null
            },
            onAutoSave = { content ->
                // 自动保存与手动保存走同一条落库路径；新笔记的 target.entryId
                // 是进入编辑器时就定下的草稿 id，反复保存都会覆盖同一条记录。
                onSaveEntry(target.entryId, content, target.categoryId)
            },
            onToggleAutoSave = onToggleAutoSave,
            onDelete = target.entryId?.let { entryId ->
                {
                    onDeleteEntry(entryId)
                    editorTarget = null
                }
            },
        )
        return
    }

    categoryNameTarget?.let { nameTarget ->
        CategoryNameDialog(
            initialName = nameTarget.initialName,
            existingNames = state.categories
                .filterNot { it.id == nameTarget.categoryId }
                .map { it.name },
            onDismiss = { categoryNameTarget = null },
            onSave = { name ->
                if (nameTarget.categoryId == null) {
                    onCreateCategory(name)
                } else {
                    onRenameCategory(nameTarget.categoryId, name)
                }
                categoryNameTarget = null
            },
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除分类？") },
            text = {
                Text(
                    if (category.count == 0) {
                        "分类“${category.name}”将被删除，此操作无法撤销。"
                    } else {
                        "分类“${category.name}”及其中 ${category.count} 篇日记将被删除，此操作无法撤销。"
                    },
                )
            },
            confirmButton = {
                ClaudeTextButton(
                    onClick = {
                        onDeleteCategory(category.id)
                        deletingCategory = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                ClaudeTextButton(onClick = { deletingCategory = null }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    val selectedCategory = state.selectedCategory
    val openNewEntryEditor: (JournalCategoryUiModel) -> Unit = { category ->
        editorTarget = JournalEditorTarget(
            // 草稿 id 提前定好：自动保存第一次落库后就固定成同一条记录。
            entryId = UUID.randomUUID().toString(),
            categoryId = category.id,
            categoryName = category.name,
            initialContent = "",
            dateLabel = state.dateLabel,
        )
    }

    // 透明底：页面下面垫的是导航层统一画的像素背景。
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                JournalHeader(dateLabel = state.dateLabel)
            }

            item(key = "categories") {
                CategorySection(
                    categories = state.categories,
                    selectedCategory = selectedCategory,
                    onCategoryClick = onCategoryClick,
                    onAddCategoryClick = {
                        categoryNameTarget = CategoryNameTarget(
                            categoryId = null,
                            initialName = "",
                        )
                    },
                    onAddEntryClick = {
                        selectedCategory?.let(openNewEntryEditor)
                    },
                    onRenameCategoryClick = {
                        selectedCategory?.let { category ->
                            categoryNameTarget = CategoryNameTarget(
                                categoryId = category.id,
                                initialName = category.name,
                            )
                        }
                    },
                    onDeleteCategoryClick = {
                        selectedCategory?.let { deletingCategory = it }
                    },
                )
            }

            if (selectedCategory == null) {
                item(key = "no-category") {
                    NoCategoryCard(
                        onAddCategoryClick = {
                            categoryNameTarget = CategoryNameTarget(
                                categoryId = null,
                                initialName = "",
                            )
                        },
                    )
                }
            } else {
                if (state.visibleEntries.isEmpty()) {
                    item(key = "empty-${selectedCategory.id}") {
                        EmptyCategoryCard(
                            categoryName = selectedCategory.name,
                            onNewEntryClick = { openNewEntryEditor(selectedCategory) },
                        )
                    }
                } else {
                    items(
                        items = state.visibleEntries,
                        key = { entry -> entry.id },
                    ) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            onClick = {
                                onEntryClick(entry.id)
                                editorTarget = JournalEditorTarget(
                                    entryId = entry.id,
                                    categoryId = entry.categoryId,
                                    categoryName = selectedCategory.name,
                                    initialContent = entry.content,
                                    dateLabel = entry.dateLabel,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEditorScreen(
    target: JournalEditorTarget,
    autoSaveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onAutoSave: (String) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var content by remember(target.entryId, target.categoryId) {
        // 打开已有笔记时光标直接落在末尾，接着上次的进度继续写。
        mutableStateOf(
            TextFieldValue(
                text = target.initialContent,
                selection = TextRange(target.initialContent.length),
            ),
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 最近一次已落库的内容（手动或自动都算），用来判断是否需要再存一次，
    // 避免打开旧笔记后不动任何键也立刻回写一遍。
    var savedContent by remember(target.entryId, target.categoryId) {
        mutableStateOf(target.initialContent)
    }
    var autoSavedHintVisible by remember { mutableStateOf(false) }
    val normalizedContent = content.text.trim()
    val characterCount = content.text.count { !it.isWhitespace() }

    // ===== 输入法适配（参考小米笔记的书写体验）=====
    // 编辑区是「外层 verticalScroll + 自适应高度的输入框」：输入框按内容完全展开。
    // 光标跟随由下面的 scrollCursorIntoView 负责：光标始终停在键盘上方一行，
    // 换行时正文整体向上走一行，用户不需要自己滑动。
    val editorScroll = remember(target.entryId, target.categoryId) { ScrollState(0) }
    var editorTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 排版结果与它对应的文本：滚动只有在「排版已跟上当前文本」时才做，
    // 否则会拿上一帧的行位置去算滚动量，新行照样落在键盘下面。
    var editorLayoutedText by remember { mutableStateOf("") }
    var editorViewportHeightPx by remember { mutableStateOf(0) }
    var editorMetadataHeightPx by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    // 键盘底边相对屏幕顶部的高度（px）；0 表示输入法没有弹出。
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    // 窗口高度用于把编辑区内部的坐标换算成屏幕坐标；取不到时用视口兜底。
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    // 光标行在滚动内容里的坐标 = 编辑区元信息行 + 行相对输入框内容区的顶边 + 输入框内边距。
    val editorTextVerticalPaddingPx = with(density) { 8.dp.toPx() }
    // 「书写行」距键盘顶线的余量：每加一行就把页面抬起一行，光标始终停在这条线上。
    val cursorScreenClearancePx = with(density) { 8.dp.toPx() }
    // 行高跟随正文排版；首行是标题时用的是标题行自己的行高（由排版结果给出）。
    val editorLineHeightPx = MaterialTheme.typography.bodyLarge.lineHeight
    // 光标下方保留的行数：前 7 行走「文字从顶部往下排」，到第 8 行才把页面抬起来，
    // 此后光标固定停在第 8 行（可视区底部往上 7 行处），不会一路滑到最底下才开始动。
    // 这 7 行同时就是滚动余量：内容够到第 8 行时，滚动范围已经足够把光标停在这里，
    // 因此不需要在正文末尾再垫一个占位（那会让短笔记底部多出一块可滚的空白）。
    val linesKeptBelowCursor = 7

    suspend fun scrollCursorIntoView() {
        if (editorViewportHeightPx <= 0) return
        // 排版还没跟上当前文本时不动：等它跟上的那一帧再滚。
        if (editorLayoutedText != content.text) return
        val layout = editorTextLayout ?: return

        val cursor = content.selection.end.coerceIn(0, content.text.length)
        val line = layout.getLineForOffset(cursor)
        val drawTopPx =
            (editorMetadataHeightPx + layout.getLineTop(line) + editorTextVerticalPaddingPx).toInt()

        val delta = cursorScrollDelta(
            drawTopPx = drawTopPx,
            currentScrollPx = editorScroll.value,
            // 下边界取**编辑区自己的高度**，不依赖键盘 inset：键盘是缩小窗口还是盖在
            // 窗口上，编辑区被分到的高度都已经把键盘让出去了，这条基准与机型无关。
            editorViewportHeightPx = editorViewportHeightPx,
            cursorClearancePx = cursorScreenClearancePx.toInt(),
            lineHeightPx = with(density) { editorLineHeightPx.toPx() }.toInt(),
            linesKeptBelowCursor = linesKeptBelowCursor,
        )
        if (delta == 0) return

        // 滚动范围上限要在行高变化后重新取；取到 0 说明这一帧还没有可滚的余量，下一帧再来。
        val target = (editorScroll.value + delta).coerceIn(0, editorScroll.maxValue)
        if (target != editorScroll.value) editorScroll.animateScrollTo(target)
    }

    // 新行排版和滚动范围要在同一帧完成后才能得到准确坐标；等一帧再滚，
    // 避免新行刚出现时仍按上一帧的光标行判断，落到可视区下面。
    // 这一帧的重排也会触发输入框自带的 bringIntoView，但它的目标只是「光标别被裁掉」，
    // 比书写行低 7 行；把光标抬到书写行（第 8 行）由这里负责。
    LaunchedEffect(
        content.text,
        content.selection,
        imeBottomPx,
        editorViewportHeightPx,
        editorMetadataHeightPx,
        windowHeightPx,
        editorLayoutedText,
    ) {
        if (editorTextLayout != null) {
            withFrameNanos { }
            scrollCursorIntoView()
        }
    }

    val titleTypography = MaterialTheme.typography.headlineSmall
    val bodyTypography = MaterialTheme.typography.bodyLarge
    // 主题色要在可组合上下文里先取好：remember 的计算块不是 @Composable。
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val titleColor = MaterialTheme.colorScheme.onBackground
    val firstLineTransformation = remember(titleTypography, bodyTypography, hintColor, titleColor) {
        // 样式统一从 TextStyle 派生（SpanStyle 带 color 的构造在当前 Compose 版本里是隐藏 API）：
        // 标题行 = 提示用的 headlineSmall 换成正文色、加重字重；两行提示保留各自的度量。
        val titleStyle = titleTypography.copy(color = titleColor)
        FirstLineTitleTransformation(
            titleStyle = titleStyle.copy(fontWeight = FontWeight.SemiBold).toSpanStyle(),
            // 空笔记的提示与光标共用同一套排版：提示就是输入框自己的文本，
            // 光标和「标题」必然落在同一个行盒里，不会各自按自己的字体度量错开。
            titleHintStyle = titleStyle.copy(color = hintColor).toSpanStyle(),
            bodyHintStyle = bodyTypography.copy(color = hintColor).toSpanStyle(),
        )
    }

    // 自动保存：内容停更约 1.5 秒且有变化时落库一次。写一半被系统杀掉或误退出，
    // 已写的正文也在列表里。开关关闭时完全不排这个定时器。
    if (autoSaveEnabled) {
        LaunchedEffect(content.text, autoSaveEnabled) {
            if (normalizedContent.isNotEmpty() && normalizedContent != savedContent.trim()) {
                delay(1_500)
                onAutoSave(content.text)
                savedContent = content.text
                autoSavedHintVisible = true
                delay(2_000)
                autoSavedHintVisible = false
            }
        }
    }

    BackHandler(onBack = onBack)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这篇日记？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                ClaudeTextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                ClaudeTextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                    ),
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClaudeIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                ClaudeFilterChip(
                    selected = autoSaveEnabled,
                    onClick = { onToggleAutoSave(!autoSaveEnabled) },
                    label = { Text("自动保存") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                if (onDelete != null) {
                    ClaudeIconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除日记",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                ClaudeIconButton(
                    onClick = { onSave(normalizedContent) },
                    enabled = normalizedContent.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "保存",
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 工具栏固定，分类、日期和正文作为一个文档一起滚动，和参考录屏一致。
            //
            // 视口高度必须量在 `verticalScroll` **外面**：`verticalScroll` 会用无界高度测量
            // 孩子（这正是长文能一行行排下去的原因），所以滚动容器内部的 `onSizeChanged`
            // 拿到的是**内容高度**，会随行数一路涨（实测从 844 涨到 1674），
            // 拿它当视口算落笔位置必然错，表现就是「要写到最底下才开始滚」。
            //
            // `imePadding()` 也加在这一层：这台设备上键盘弹出时窗口不让位（imeBottom=598
            // 而内容高度不变），不加这一层键盘会直接盖住正文，光标行的位置算得再准也看不见。
            // 它消费 IME inset 后，`weight(1f)` 分到的高度就是真正可见的那块，量出来的视口即所见。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .onSizeChanged { editorViewportHeightPx = it.height },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(editorScroll)
                        // 点击输入框以外的空白区域：光标落到文末并拉起输入法，
                        // 内容不足一屏时也能随手继续写。
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            content = content.copy(selection = TextRange(content.text.length))
                            focusRequester.requestFocus()
                        },
                ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { editorMetadataHeightPx = it.height }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = target.categoryName,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(
                            text = if (autoSavedHintVisible) {
                                "${target.dateLabel}  ·  ${characterCount}字  ·  已自动保存"
                            } else {
                                "${target.dateLabel}  ·  ${characterCount}字"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 空笔记的两行提示由输入框自己的 VisualTransformation 渲染，
                    // 叠一层 Text 会让提示与光标各自排版、上下错开。
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BasicTextField(
                                value = content,
                                onValueChange = { content = it },
                                onTextLayout = { layoutResult ->
                                    editorTextLayout = layoutResult
                                    editorLayoutedText = content.text
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.claude.accent),
                                visualTransformation = firstLineTransformation,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * 「书写行」的下边界：光标固定停在这里，视口底部往上还留着若干行的高度。
 *
 * 基准是**视口的真实高度**（带 `weight(1f)` 的外层 Box 量到的那个），不是 `verticalScroll`
 * 内部量到的尺寸——后者是内容高度，会随行数一路涨，用它算出来的落笔位置会一直往下跑，
 * 表现就是「写到最底下才开始滚」。也不直接依赖键盘 inset：窗口有没有被键盘缩小、
 * 编辑区被分到多少高度，`weight` + `imePadding()` 已经把这笔账算完了。
 *
 * 保留行数是**上限**而不是硬指标：键盘弹出后可视区可能只剩五六行，
 * 硬留 7 行会把光标顶到视口之上（人看不见自己在写什么），因此在放不下时按
 * [maxReserveFraction] 折半保留——挪笔位置让位于「光标必须看得见」。
 */
internal fun writingLineBottomPx(
    editorViewportHeightPx: Int,
    lineHeightPx: Int,
    linesKeptBelowCursor: Int,
    maxReserveFraction: Double = 0.5,
): Int {
    if (lineHeightPx <= 0) return editorViewportHeightPx
    val wantedPx = linesKeptBelowCursor * lineHeightPx
    val allowedPx = (editorViewportHeightPx * maxReserveFraction).toInt()
    val reservePx = minOf(wantedPx, allowedPx, editorViewportHeightPx - lineHeightPx)
    return editorViewportHeightPx - reservePx
}

/**
 * 自适应滑动的口径：光标始终停在「书写行」上，也就是 [writingLineBottomPx] 那条线往上
 * [cursorClearancePx] 处。
 *
 * 每次换行只在下面多出一行，光标越过书写行 → 页面整体上移**一行行高**，把光标送回书写行；
 * 首行是标题时，这一行的行高就是标题行的行高（[drawTopPx] 由排版结果给出，天然如此）。
 * 于是连续书写时正文一行行向上走，用户不需要自己滑动。
 *
 * 返回需要滚动的**增量**（0 表示不用动）：增量等于越界的量，所以新行越界一行只滚一行。
 * 光标本来就在书写行与视口顶部之间时返回 0，避免每敲一个字都重新滚动而抖动。
 */
internal fun cursorScrollDelta(
    drawTopPx: Int,
    currentScrollPx: Int,
    editorViewportHeightPx: Int,
    cursorClearancePx: Int,
    lineHeightPx: Int,
    linesKeptBelowCursor: Int,
    maxReserveFraction: Double = 0.5,
): Int {
    // 先看光标在可视区里的相对位置，再与书写行比较，全程不碰窗口/键盘坐标。
    val cursorInViewportPx = drawTopPx - currentScrollPx
    val editableBottomPx = writingLineBottomPx(
        editorViewportHeightPx = editorViewportHeightPx,
        lineHeightPx = lineHeightPx,
        linesKeptBelowCursor = linesKeptBelowCursor,
        maxReserveFraction = maxReserveFraction,
    )

    if (cursorInViewportPx + cursorClearancePx > editableBottomPx) {
        return cursorInViewportPx + cursorClearancePx - editableBottomPx
    }
    if (cursorInViewportPx < 0) {
        // 光标跑到视口上方（长按删除、返回上一行、粘贴）时把它拉回来。
        return cursorInViewportPx - cursorClearancePx
    }
    return 0
}

private class FirstLineTitleTransformation(
    private val titleStyle: SpanStyle,
    private val titleHintStyle: SpanStyle,
    private val bodyHintStyle: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // 空笔记：原文 0 字符、变换后是两行提示。**不能用 OffsetMapping.Identity**——
        // 恒等映射会把提示的每个偏移原样报回原文，而原文长度是 0，
        // Compose 的校验（ValidatingOffsetMapping）会直接抛
        // 「1 -> 1 is not in range of original text [0, 0]」，编辑器一打开就闪退。
        if (raw.isEmpty()) return TransformedText(hint(), EmptyTextHintOffsetMapping)
        val firstLineEnd = raw.indexOf('\n').let { index ->
            if (index == -1) raw.length else index
        }
        val transformed = buildAnnotatedString {
            append(text)
            if (firstLineEnd > 0) addStyle(titleStyle, 0, firstLineEnd)
        }
        return TransformedText(transformed, OffsetMapping.Identity)
    }

    /**
     * 空笔记的提示：用输入框自己的 `TransformedText` 承载，而不是叠一层 Text。
     * 两行提示的换行与行高都写在这里，光标因此与「标题」共享同一个行盒。
     */
    private fun hint(): AnnotatedString = buildAnnotatedString {
        withStyle(titleHintStyle) { append("标题") }
        append("\n")
        withStyle(bodyHintStyle) { append("从第二行开始书写正文…") }
    }
}

/**
 * 空文本 + 多字符提示专用的偏移映射。
 *
 * 提示只作展示，不承载任何真实文本，因此两个方向都收敛到 0：
 * 原文没有字符，可见文本里的任何位置都属于提示，光标只能在最前面。
 * 若在这里用 `OffsetMapping.Identity`，Compose 会在校验时抛
 * 「transformedToOriginal returned invalid mapping」，编辑器一打开就闪退。
 */
internal object EmptyTextHintOffsetMapping : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = 0

    override fun transformedToOriginal(offset: Int): Int = 0
}

@Composable
private fun CategoryNameDialog(
    initialName: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalizedName = name.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "新建分类" else "重命名分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分类名称") },
                placeholder = { Text("例如：读书笔记") },
                singleLine = true,
                isError = isDuplicate,
                supportingText = {
                    Text(if (isDuplicate) "已经有同名分类" else "${name.length} / 20")
                },
            )
        },
        confirmButton = {
            ClaudeTextButton(
                onClick = { onSave(normalizedName) },
                enabled = normalizedName.isNotEmpty() && !isDuplicate,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            ClaudeTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun JournalHeader(
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "日记随想",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = dateLabel,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CategorySection(
    categories: List<JournalCategoryUiModel>,
    selectedCategory: JournalCategoryUiModel?,
    onCategoryClick: (String) -> Unit,
    onAddCategoryClick: () -> Unit,
    onAddEntryClick: () -> Unit,
    onRenameCategoryClick: () -> Unit,
    onDeleteCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showManageMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的分类",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            ClaudeTextButton(
                onClick = onAddEntryClick,
                enabled = selectedCategory != null,
            ) {
                Text("新建笔记")
            }
            ClaudeTextButton(onClick = onAddCategoryClick) {
                Text("新建分类")
            }
            Box {
                ClaudeIconButton(
                    onClick = { showManageMenu = true },
                    enabled = selectedCategory != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "重命名或删除当前分类",
                    )
                }
                DropdownMenu(
                    expanded = showManageMenu,
                    onDismissRequest = { showManageMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名分类") },
                        onClick = {
                            showManageMenu = false
                            onRenameCategoryClick()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("删除分类", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showManageMenu = false
                            onDeleteCategoryClick()
                        },
                    )
                }
            }
        }

        if (selectedCategory != null) {
            Text(
                text = "新笔记会保存到「${selectedCategory.name}」",
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val isSelected = category.id == selectedCategory?.id
                    ClaudeFilterChip(
                        selected = isSelected,
                        onClick = { onCategoryClick(category.id) },
                        label = { Text("${category.name} ${category.count}") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.claude.accent.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoCategoryCard(
    onAddCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "分类是日记的存放位置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ClaudeButton(
                onClick = onAddCategoryClick,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("创建分类")
            }
        }
    }
}

@Composable
private fun EmptyCategoryCard(
    categoryName: String,
    onNewEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "“$categoryName”中还没有日记",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "写下第一篇，从这一刻开始。",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ClaudeTextButton(onClick = onNewEntryClick) {
                Text("写一篇")
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().pressBounce(interactionSource, pressedScale = 0.97f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title.ifBlank { "无标题" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.dateLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.snippet.isNotEmpty()) {
                Text(
                    text = entry.snippet,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(
    name = "Default",
    group = "Journal",
    showBackground = true,
    widthDp = 412,
    heightDp = 1200,
)
@Composable
fun JournalScreenPreview() {
    CampusLifeTheme(darkTheme = false) {
        JournalScreen(state = JournalPreviewData.default)
    }
}

@Preview(
    name = "Empty state",
    group = "Journal",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun JournalScreenEmptyPreview() {
    CampusLifeTheme(darkTheme = false) {
        JournalScreen(state = JournalPreviewData.empty)
    }
}

@Preview(
    name = "Dark",
    group = "Journal",
    showBackground = true,
    widthDp = 412,
    heightDp = 1200,
)
@Composable
fun JournalScreenDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        JournalScreen(state = JournalPreviewData.default)
    }
}
