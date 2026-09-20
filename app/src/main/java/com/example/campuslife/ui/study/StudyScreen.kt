package com.example.campuslife.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat
import com.example.campuslife.ui.theme.CampusLifeTheme

@Composable
fun StudyScreen(
    state: StudyUiState,
    modifier: Modifier = Modifier,
    onPickFilesClick: () -> Unit = {},
    onPickFolderClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSelectCategory: (StudyCategory) -> Unit = {},
    onDocumentClick: (String) -> Unit = {},
    onSetDocumentCategory: (String, StudyCategory) -> Unit = { _, _ -> },
    onDeleteDocument: (String) -> Unit = {},
    onDismissImportResult: () -> Unit = {},
) {
    var showImportOptions by remember { mutableStateOf(false) }
    var documentForActions by remember { mutableStateOf<StudyDocumentUiModel?>(null) }
    var documentForDelete by remember { mutableStateOf<StudyDocumentUiModel?>(null) }

    if (showImportOptions) {
        ImportSourceDialog(
            category = state.selectedCategory,
            onDismiss = { showImportOptions = false },
            onPickFiles = {
                showImportOptions = false
                onPickFilesClick()
            },
            onPickFolder = {
                showImportOptions = false
                onPickFolderClick()
            },
        )
    }

    if (state.isImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在创建离线副本") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("正在校验格式与资源，请稍候…")
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    state.importResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissImportResult,
            title = { Text(if (result.hasImportedDocuments) "导入完成" else "没有导入资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result.importedCount > 0) Text("已添加 ${result.importedCount} 份资料。")
                    if (result.skippedDuplicates > 0) Text("已跳过 ${result.skippedDuplicates} 份内容相同的资料。")
                    result.errors.forEach { error ->
                        Text("• $error", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissImportResult) {
                    Text("知道了")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    documentForActions?.let { document ->
        AlertDialog(
            onDismissRequest = { documentForActions = null },
            title = { Text(document.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "所在分类",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StudyCategory.entries.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetDocumentCategory(document.id, category)
                                    documentForActions = null
                                }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = document.category == category,
                                onClick = {
                                    onSetDocumentCategory(document.id, category)
                                    documentForActions = null
                                },
                            )
                            Text(
                                text = category.label,
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { documentForActions = null }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        documentForDelete = document
                        documentForActions = null
                    },
                ) {
                    Text("删除副本", color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    documentForDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentForDelete = null },
            title = { Text("删除“${document.title}”？") },
            text = {
                Text("删除后只会移除 App 内的离线副本和阅读进度，不会删除你最初选择的源文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDocument(document.id)
                        documentForDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentForDelete = null }) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
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
                StudyLibraryHeader(onAddDocumentClick = { showImportOptions = true })
            }

            item(key = "categories") {
                StudyCategorySelector(
                    selectedCategory = state.selectedCategory,
                    onSelectCategory = onSelectCategory,
                )
            }

            item(key = "search") {
                DocumentSearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                )
            }

            item(key = "library-heading") {
                LibraryHeading(summaryLabel = state.librarySummaryLabel)
            }

            if (state.documents.isEmpty() && state.totalDocumentCount == 0) {
                item(key = "empty-library") {
                    EmptyDocumentLibrary(
                        category = state.selectedCategory,
                        onAddDocumentClick = { showImportOptions = true },
                    )
                }
            } else if (state.documents.isEmpty()) {
                item(key = "no-results") {
                    NoDocumentResults()
                }
            } else {
                item(key = "documents") {
                    DocumentList(
                        documents = state.documents,
                        onDocumentClick = onDocumentClick,
                        onDocumentMoreClick = { id ->
                            documentForActions = state.documents.firstOrNull { it.id == id }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyLibraryHeader(
    onAddDocumentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "学习资料",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "把电脑里的笔记带在身边",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(
            onClick = onAddDocumentClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("添加")
        }
    }
}

@Composable
private fun ImportSourceDialog(
    category: StudyCategory,
    onDismiss: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到“${category.label}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPickFiles,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("选择文件")
                        Text(
                            "Markdown 或 ZIP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("选择资料文件夹")
                        Text(
                            "保留相对路径图片与附件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun StudyCategorySelector(
    selectedCategory: StudyCategory,
    onSelectCategory: (StudyCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudyCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = category.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DocumentSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索标题、文件夹或文件名") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun LibraryHeading(
    summaryLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "资料库",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summaryLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentList(
    documents: List<StudyDocumentUiModel>,
    onDocumentClick: (String) -> Unit,
    onDocumentMoreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        documents.forEachIndexed { index, document ->
            DocumentRow(
                document = document,
                onClick = { onDocumentClick(document.id) },
                onMoreClick = { onDocumentMoreClick(document.id) },
            )
            if (index < documents.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: StudyDocumentUiModel,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = document.progress.coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt().coerceIn(0, 100)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 13.dp, end = 4.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentIcon(format = document.format)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = document.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${document.folderLabel} · ${document.detailLabel}",
                    modifier = Modifier.padding(start = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "更多文件操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentIcon(
    format: StudyDocumentFormat,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (format) {
        StudyDocumentFormat.Markdown -> Icons.Outlined.Description
    }
    Surface(
        modifier = modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = formatContainerColor(format),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = formatAccentColor(format),
            )
        }
    }
}

@Composable
private fun EmptyDocumentLibrary(
    category: StudyCategory,
    onAddDocumentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = when (category) {
                    StudyCategory.Vocabulary -> "添加要背的单词资料"
                    StudyCategory.Notes -> "添加要看的学习笔记"
                    StudyCategory.Documents -> "添加要阅读的文档"
                },
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onAddDocumentClick,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("添加到${category.label}")
            }
        }
    }
}

@Composable
private fun NoDocumentResults(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "没有匹配的资料",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun formatContainerColor(format: StudyDocumentFormat) = when (format) {
    StudyDocumentFormat.Markdown -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun formatAccentColor(format: StudyDocumentFormat) = when (format) {
    StudyDocumentFormat.Markdown -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Preview(
    name = "Library",
    group = "Study",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun StudyScreenPreview() {
    CampusLifeTheme(darkTheme = false) {
        StudyScreen(state = StudyPreviewData.default)
    }
}

@Preview(
    name = "Empty library",
    group = "Study",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun StudyScreenEmptyPreview() {
    CampusLifeTheme(darkTheme = false) {
        StudyScreen(state = StudyPreviewData.empty)
    }
}
