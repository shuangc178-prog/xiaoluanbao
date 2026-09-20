package com.example.campuslife.data.study

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

class StudyImporter(
    private val context: Context,
    private val store: StudyStore,
) {
    val importsDirectory: File = File(context.filesDir, IMPORTS_DIRECTORY_NAME)

    suspend fun importFiles(
        uris: List<Uri>,
        category: StudyCategory = StudyCategory.Notes,
    ): StudyImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext StudyImportResult(importedCount = 0)

        val batches = mutableListOf<ImportBatch>()
        val errors = mutableListOf<String>()
        uris.distinct().forEach { uri ->
            val metadata = readUriMetadata(uri)
            runCatching {
                when (metadata.displayName.substringAfterLast('.', "").lowercase()) {
                    "zip" -> importZip(uri, metadata, category)
                    "md", "markdown" -> importSingleDocument(uri, metadata, category)
                    else -> throw IOException("不支持的格式：${metadata.displayName}")
                }
            }.onSuccess(batches::add)
                .onFailure { error -> errors += error.message ?: "无法导入 ${metadata.displayName}" }
        }

        persistBatches(batches, errors)
    }

    suspend fun importFolder(
        treeUri: Uri,
        category: StudyCategory = StudyCategory.Notes,
    ): StudyImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val batch = runCatching { importDocumentTree(treeUri, category) }
            .onFailure { error -> errors += error.message ?: "无法读取所选文件夹" }
            .getOrNull()
        persistBatches(listOfNotNull(batch), errors)
    }

    suspend fun deleteDocument(document: StudyDocument) = withContext(Dispatchers.IO) {
        store.delete(document.id)
        val snapshotStillUsed = store.documents.first().any { it.snapshotId == document.snapshotId }
        if (!snapshotStillUsed) deleteSnapshot(document.snapshotId)
    }

    fun documentFile(document: StudyDocument): File? {
        val snapshotRoot = File(importsDirectory, document.snapshotId)
        val candidate = File(snapshotRoot, document.relativePath)
        return candidate.takeIf {
            it.isFile && it.canonicalPath.startsWith(snapshotRoot.canonicalPath + File.separator)
        }
    }

    private suspend fun persistBatches(
        batches: List<ImportBatch>,
        initialErrors: List<String>,
    ): StudyImportResult {
        val errors = initialErrors.toMutableList()
        val existingHashes = store.documents.first().mapTo(mutableSetOf()) { it.contentHash }
        val accepted = mutableListOf<StudyDocument>()
        var skipped = 0

        batches.forEach { batch ->
            val acceptedFromBatch = batch.documents.filter { document ->
                if (document.contentHash in existingHashes) {
                    skipped += 1
                    false
                } else {
                    existingHashes += document.contentHash
                    true
                }
            }
            accepted += acceptedFromBatch
            if (acceptedFromBatch.isEmpty()) deleteSnapshot(batch.snapshotId)
        }

        var persisted = true
        if (accepted.isNotEmpty()) {
            runCatching { store.upsertAll(accepted) }
                .onFailure { error ->
                    persisted = false
                    accepted.map { it.snapshotId }.distinct().forEach(::deleteSnapshot)
                    errors += error.message ?: "资料索引保存失败"
                }
        }

        return StudyImportResult(
            importedCount = if (persisted) accepted.size else 0,
            skippedDuplicates = skipped,
            errors = errors,
        )
    }

    private fun importSingleDocument(
        uri: Uri,
        metadata: UriMetadata,
        category: StudyCategory,
    ): ImportBatch {
        val snapshotId = UUID.randomUUID().toString()
        val root = createSnapshotDirectory(snapshotId)
        return try {
            val safeName = safeFileName(metadata.displayName)
            val target = File(root, safeName)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开 ${metadata.displayName}" }
                copyWithLimits(input, target, ImportBudget())
            }
            if (metadata.modifiedAtEpochMillis > 0) target.setLastModified(metadata.modifiedAtEpochMillis)
            ImportBatch(
                snapshotId = snapshotId,
                documents = listOf(
                    createDocument(
                        file = target,
                        snapshotId = snapshotId,
                        relativePath = safeName,
                        sourceName = metadata.displayName,
                        folderLabel = "单文件",
                        sourceModifiedAt = metadata.modifiedAtEpochMillis,
                        category = category,
                    ),
                ),
            )
        } catch (error: Throwable) {
            deleteSnapshot(snapshotId)
            throw error
        }
    }

    private fun importDocumentTree(treeUri: Uri, category: StudyCategory): ImportBatch {
        val sourceRoot = DocumentFile.fromTreeUri(context, treeUri)
            ?.takeIf { it.isDirectory && it.canRead() }
            ?: throw IOException("无法读取所选文件夹")
        val snapshotId = UUID.randomUUID().toString()
        val root = createSnapshotDirectory(snapshotId)
        val budget = ImportBudget()
        return try {
            sourceRoot.listFiles().forEach { child ->
                copyDocumentTreeNode(child, root, budget)
            }
            val files = supportedDocuments(root)
            if (files.isEmpty()) throw IOException("文件夹中没有 Markdown 文件")
            val folderName = sourceRoot.name?.takeIf { it.isNotBlank() } ?: "资料文件夹"
            ImportBatch(
                snapshotId = snapshotId,
                documents = files.map { file ->
                    createDocument(
                        file = file,
                        snapshotId = snapshotId,
                        relativePath = file.relativeTo(root).invariantSeparatorsPath,
                        sourceName = file.relativeTo(root).invariantSeparatorsPath,
                        folderLabel = folderName,
                        sourceModifiedAt = file.lastModified(),
                        category = category,
                    )
                },
            )
        } catch (error: Throwable) {
            deleteSnapshot(snapshotId)
            throw error
        }
    }

    private fun importZip(
        uri: Uri,
        metadata: UriMetadata,
        category: StudyCategory,
    ): ImportBatch {
        val snapshotId = UUID.randomUUID().toString()
        val root = createSnapshotDirectory(snapshotId)
        val budget = ImportBudget()
        return try {
            context.contentResolver.openInputStream(uri).use { rawInput ->
                requireNotNull(rawInput) { "无法打开 ${metadata.displayName}" }
                ZipInputStream(rawInput.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        budget.registerEntry()
                        val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                        if (normalizedName.isNotBlank() && !normalizedName.startsWith("__MACOSX/")) {
                            val target = safeZipTarget(root, normalizedName)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                copyWithLimits(zip, target, budget, closeInput = false)
                                if (entry.time > 0) target.setLastModified(entry.time)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val files = supportedDocuments(root)
            if (files.isEmpty()) throw IOException("压缩包中没有 Markdown 文件")
            val folderLabel = metadata.displayName.substringBeforeLast('.').ifBlank { "资料包" }
            ImportBatch(
                snapshotId = snapshotId,
                documents = files.map { file ->
                    createDocument(
                        file = file,
                        snapshotId = snapshotId,
                        relativePath = file.relativeTo(root).invariantSeparatorsPath,
                        sourceName = file.relativeTo(root).invariantSeparatorsPath,
                        folderLabel = folderLabel,
                        sourceModifiedAt = file.lastModified(),
                        category = category,
                    )
                },
            )
        } catch (error: Throwable) {
            deleteSnapshot(snapshotId)
            throw error
        }
    }

    private fun copyDocumentTreeNode(
        source: DocumentFile,
        targetParent: File,
        budget: ImportBudget,
    ) {
        budget.registerEntry()
        val name = safeFileName(source.name ?: "未命名")
        val target = uniqueChild(targetParent, name)
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("无法创建目录：$name")
            source.listFiles().forEach { child -> copyDocumentTreeNode(child, target, budget) }
        } else if (source.isFile) {
            context.contentResolver.openInputStream(source.uri).use { input ->
                requireNotNull(input) { "无法读取：$name" }
                copyWithLimits(input, target, budget)
            }
            if (source.lastModified() > 0) target.setLastModified(source.lastModified())
        }
    }

    private fun createDocument(
        file: File,
        snapshotId: String,
        relativePath: String,
        sourceName: String,
        folderLabel: String,
        sourceModifiedAt: Long,
        category: StudyCategory,
    ): StudyDocument {
        val format = formatOf(file) ?: throw IOException("不支持的格式：${file.name}")
        val now = System.currentTimeMillis()
        return StudyDocument(
            id = UUID.randomUUID().toString(),
            title = extractTitle(file, format),
            format = format,
            sourceName = sourceName,
            folderLabel = folderLabel,
            snapshotId = snapshotId,
            relativePath = relativePath,
            sizeBytes = file.length(),
            sourceModifiedAtEpochMillis = sourceModifiedAt.coerceAtLeast(0L),
            importedAtEpochMillis = now,
            contentHash = sha256(file),
            category = category,
        )
    }

    private fun readUriMetadata(uri: Uri): UriMetadata {
        var displayName: String? = null
        var size = 0L
        var modified = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { displayName = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { size = cursor.getLong(it) }
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { modified = cursor.getLong(it) }
                }
            }
        }
        return UriMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "未命名资料",
            sizeBytes = size,
            modifiedAtEpochMillis = modified,
        )
    }

    private fun createSnapshotDirectory(snapshotId: String): File {
        importsDirectory.mkdirs()
        return File(importsDirectory, snapshotId).also { root ->
            if (!root.mkdirs() || !root.isDirectory) throw IOException("无法创建本地资料副本")
        }
    }

    private fun deleteSnapshot(snapshotId: String) {
        val root = File(importsDirectory, snapshotId)
        val importsPath = importsDirectory.canonicalPath + File.separator
        if (root.canonicalPath.startsWith(importsPath) && root.isDirectory) root.deleteRecursively()
    }

    private fun supportedDocuments(root: File): List<File> = root.walkTopDown()
        .maxDepth(MAX_TREE_DEPTH)
        .filter { it.isFile && formatOf(it) != null }
        .toList()

    private fun formatOf(file: File): StudyDocumentFormat? = when (file.extension.lowercase()) {
        "md", "markdown" -> StudyDocumentFormat.Markdown
        else -> null
    }

    private fun extractTitle(file: File, format: StudyDocumentFormat): String {
        val fallback = file.nameWithoutExtension.ifBlank { "未命名资料" }
        val text = runCatching {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_TITLE_SCAN_CHARS)
                val count = reader.read(buffer)
                if (count <= 0) "" else String(buffer, 0, count).removePrefix("\uFEFF")
            }
        }.getOrDefault("")

        val candidate = MARKDOWN_TITLE.find(text)?.groupValues?.getOrNull(1)
        return candidate
            ?.replace(MARKDOWN_DECORATION, "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun copyWithLimits(
        input: InputStream,
        target: File,
        budget: ImportBudget,
        closeInput: Boolean = true,
    ) {
        target.parentFile?.mkdirs()
        val output = FileOutputStream(target)
        try {
            output.use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    budget.addBytes(read)
                    sink.write(buffer, 0, read)
                }
            }
        } finally {
            if (closeInput) input.close()
        }
    }

    private fun safeZipTarget(root: File, entryName: String): File {
        val target = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        if (!target.canonicalPath.startsWith(rootPath)) throw IOException("压缩包包含不安全路径")
        return target
    }

    private fun safeFileName(name: String): String = name
        .replace('/', '_')
        .replace('\\', '_')
        .replace(Regex("[\\u0000-\\u001F]"), "_")
        .trim()
        .take(MAX_FILE_NAME_LENGTH)
        .ifBlank { "未命名" }

    private fun uniqueChild(parent: File, name: String): File {
        var candidate = File(parent, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { '.' in name }
        var index = 2
        while (candidate.exists()) {
            val suffix = extension?.let { ".$it" }.orEmpty()
            candidate = File(parent, "$base ($index)$suffix")
            index += 1
        }
        return candidate
    }

    private data class ImportBatch(
        val snapshotId: String,
        val documents: List<StudyDocument>,
    )

    private data class UriMetadata(
        val displayName: String,
        val sizeBytes: Long,
        val modifiedAtEpochMillis: Long,
    )

    private class ImportBudget {
        private var entries: Int = 0
        private var bytes: Long = 0L

        fun registerEntry() {
            entries += 1
            if (entries > MAX_IMPORT_ENTRIES) throw IOException("资料包文件过多，最多 $MAX_IMPORT_ENTRIES 个")
        }

        fun addBytes(count: Int) {
            bytes += count
            if (bytes > MAX_IMPORT_BYTES) throw IOException("资料包过大，最多 512 MB")
        }
    }

    private companion object {
        const val IMPORTS_DIRECTORY_NAME = "study_imports"
        const val MAX_IMPORT_ENTRIES = 5_000
        const val MAX_IMPORT_BYTES = 512L * 1024L * 1024L
        const val MAX_TREE_DEPTH = 32
        const val MAX_FILE_NAME_LENGTH = 180
        const val MAX_TITLE_SCAN_CHARS = 512 * 1024

        val MARKDOWN_TITLE = Regex("(?m)^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$")
        val MARKDOWN_DECORATION = Regex("[*_`~]+")
    }
}
