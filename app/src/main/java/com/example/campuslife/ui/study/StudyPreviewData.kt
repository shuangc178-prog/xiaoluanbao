package com.example.campuslife.ui.study

import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat

object StudyPreviewData {
    private fun document(
        id: String,
        title: String,
        format: StudyDocumentFormat,
        folder: String,
        detail: String,
        progress: Float = 0f,
        category: StudyCategory = StudyCategory.Notes,
    ) = StudyDocumentUiModel(
        id = id,
        title = title,
        format = format,
        sourceName = title,
        folderLabel = folder,
        detailLabel = detail,
        snapshotId = "preview-snapshot",
        relativePath = "$id.md",
        progress = progress,
        category = category,
    )

    private val pythonBasics = document(
        id = "python-basics-md",
        title = "Python 基础",
        format = StudyDocumentFormat.Markdown,
        folder = "Python 教程",
        detail = "今天 15:56 · 503 KB",
        progress = 0.42f,
    )

    val default = StudyUiState(
        librarySummaryLabel = "6 份资料 · 仅保存在本机",
        searchQuery = "",
        documents = listOf(
            pythonBasics,
            document(
                id = "network-basics-md",
                title = "计算机网络基础学习",
                format = StudyDocumentFormat.Markdown,
                folder = "计算机网络",
                detail = "今天更新 · 4.7 KB",
                progress = 0.76f,
            ),
            document(
                id = "ai-roadmap-md",
                title = "AI 应用开发学习路径",
                format = StudyDocumentFormat.Markdown,
                folder = "学习计划",
                detail = "9月4日 · 11.2 KB",
                progress = 0.18f,
            ),
            document(
                id = "cet6-day1-md",
                title = "CET-6 · Day 1",
                format = StudyDocumentFormat.Markdown,
                folder = "CET-6",
                detail = "9月12日 · 46.4 KB",
                progress = 1f,
            ),
            document(
                id = "mysql-basics-md",
                title = "MySQL 基础",
                format = StudyDocumentFormat.Markdown,
                folder = "MySQL 教程",
                detail = "今天更新 · 3.8 KB",
                progress = 0.64f,
            ),
            document(
                id = "phase-one-md",
                title = "第一阶段学习",
                format = StudyDocumentFormat.Markdown,
                folder = "学习计划",
                detail = "9月12日 · 5.7 KB",
            ),
        ),
        totalDocumentCount = 6,
    )

    val empty = StudyUiState(
        librarySummaryLabel = "还没有添加资料",
        searchQuery = "",
        documents = emptyList(),
        totalDocumentCount = 0,
    )

    val reader = StudyReaderUiState(
        document = pythonBasics.copy(category = StudyCategory.Vocabulary),
        progress = 0.42f,
        progressLabel = "已读 42%",
    )

    /** 预览里的勾选覆盖层：给第一份资料勾上第 0、2 项，Reading 预览因此能看到完成行。 */
    val checkedTasksByDocument: Map<String, Set<Int>> = mapOf(
        pythonBasics.id to setOf(0, 2),
    )
}
