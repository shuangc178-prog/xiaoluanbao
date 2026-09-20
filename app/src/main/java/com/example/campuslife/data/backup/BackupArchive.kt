package com.example.campuslife.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class ReadBackupArchive(
    val manifest: BackupArchiveManifest,
    val snapshot: AppBackupSnapshot,
)

/** `.xlbak` 是一个标准 ZIP：元数据、业务快照、学习资料文件各自独立。 */
internal object BackupArchive {
    const val FORMAT_VERSION = 1
    const val MAX_REMOTE_FILE_BYTES = 500L * 1024 * 1024

    private const val MANIFEST_ENTRY = "manifest.properties"
    private const val DATA_ENTRY = "data.bin"
    private const val STUDY_PREFIX = "study/"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_DATA_BYTES = 64 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 20_000
    private const val MAX_EXTRACTED_BYTES = 1024L * 1024 * 1024
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    fun fileName(createdAtEpochMillis: Long): String {
        val stamp = Instant.ofEpochMilli(createdAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(fileNameFormatter)
        return "xiaoluanbao_$stamp.xlbak"
    }

    fun write(
        destination: File,
        snapshot: AppBackupSnapshot,
        manifest: BackupArchiveManifest,
        studyImportsDirectory: File,
    ) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.partial")
        if (temporary.exists() && !temporary.delete()) {
            throw BackupFormatException("无法清理未完成的旧备份")
        }

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(encodeManifest(manifest))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(DATA_ENTRY))
                BackupSnapshotCodec.write(snapshot, zip.nonClosing())
                zip.closeEntry()

                if (studyImportsDirectory.isDirectory) {
                    val root = studyImportsDirectory.canonicalFile
                    studyImportsDirectory.walkTopDown()
                        .filter(File::isFile)
                        .forEach { file ->
                            val canonical = file.canonicalFile
                            if (!canonical.toPath().startsWith(root.toPath())) {
                                throw BackupFormatException("学习资料路径异常")
                            }
                            val relative = root.toPath().relativize(canonical.toPath())
                                .joinToString("/") { it.toString() }
                            validateRelativePath(relative)
                            zip.putNextEntry(ZipEntry(STUDY_PREFIX + relative))
                            FileInputStream(canonical).use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Exception) {
            temporary.delete()
            if (error is BackupFormatException) throw error
            throw BackupFormatException("生成备份失败", error)
        }
    }

    /**
     * 完整校验后返回业务快照；传入 [studyExtractionDirectory] 时同时安全解压学习资料。
     */
    fun read(file: File, studyExtractionDirectory: File? = null): ReadBackupArchive {
        if (!file.isFile || file.length() <= 0L) throw BackupFormatException("备份文件不存在或为空")
        if (file.length() > MAX_REMOTE_FILE_BYTES) throw BackupFormatException("备份文件超过 500MB")

        studyExtractionDirectory?.let { directory ->
            if (directory.exists() && !directory.deleteRecursively()) {
                throw BackupFormatException("无法清理恢复临时目录")
            }
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw BackupFormatException("无法创建恢复临时目录")
            }
        }

        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        var entryCount = 0
        var extractedBytes = 0L
        val seenNames = mutableSetOf<String>()

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES) {
                        throw BackupFormatException("备份中的文件数量异常")
                    }
                    val name = normalizeEntryName(entry.name)
                    if (!seenNames.add(name)) throw BackupFormatException("备份中存在重复文件")

                    when {
                        entry.isDirectory -> Unit
                        name == MANIFEST_ENTRY -> {
                            manifestBytes = zip.readLimited(MAX_MANIFEST_BYTES)
                            extractedBytes += manifestBytes!!.size
                        }
                        name == DATA_ENTRY -> {
                            dataBytes = zip.readLimited(MAX_DATA_BYTES)
                            extractedBytes += dataBytes!!.size
                        }
                        name.startsWith(STUDY_PREFIX) -> {
                            val relative = name.removePrefix(STUDY_PREFIX)
                            validateRelativePath(relative)
                            val targetRoot = studyExtractionDirectory?.canonicalFile
                            if (targetRoot == null) {
                                extractedBytes += zip.drainLimited(MAX_EXTRACTED_BYTES - extractedBytes)
                            } else {
                                val target = File(targetRoot, relative).canonicalFile
                                if (!target.toPath().startsWith(targetRoot.toPath())) {
                                    throw BackupFormatException("备份中存在越界路径")
                                }
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    extractedBytes += zip.copyLimited(
                                        output = output,
                                        remainingLimit = MAX_EXTRACTED_BYTES - extractedBytes,
                                    )
                                }
                            }
                        }
                        else -> throw BackupFormatException("备份中含有无法识别的文件")
                    }
                    if (extractedBytes > MAX_EXTRACTED_BYTES) {
                        throw BackupFormatException("备份解压后的大小异常")
                    }
                    zip.closeEntry()
                }
            }

            val manifest = decodeManifest(
                manifestBytes ?: throw BackupFormatException("备份缺少版本信息"),
            )
            val snapshot = BackupSnapshotCodec.read(
                ByteArrayInputStream(dataBytes ?: throw BackupFormatException("备份缺少数据")),
            )
            validateStudyReferences(snapshot, studyExtractionDirectory)
            return ReadBackupArchive(manifest = manifest, snapshot = snapshot)
        } catch (error: BackupFormatException) {
            studyExtractionDirectory?.deleteRecursively()
            throw error
        } catch (error: Exception) {
            studyExtractionDirectory?.deleteRecursively()
            throw BackupFormatException("备份文件无法读取", error)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun encodeManifest(manifest: BackupArchiveManifest): ByteArray {
        val properties = Properties().apply {
            setProperty("format", "xiaoluanbao-backup")
            setProperty("format_version", manifest.formatVersion.toString())
            setProperty("created_at", manifest.createdAtEpochMillis.toString())
            setProperty("logical_epoch_day", manifest.logicalEpochDay.toString())
            setProperty("app_version", manifest.appVersionName)
        }
        return ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray()
        }
    }

    private fun decodeManifest(bytes: ByteArray): BackupArchiveManifest {
        val properties = Properties().apply { load(ByteArrayInputStream(bytes)) }
        if (properties.getProperty("format") != "xiaoluanbao-backup") {
            throw BackupFormatException("不是小卵包备份文件")
        }
        val formatVersion = properties.getProperty("format_version")?.toIntOrNull()
            ?: throw BackupFormatException("备份版本信息无效")
        if (formatVersion != FORMAT_VERSION) {
            throw BackupFormatException("暂不支持备份格式版本 $formatVersion")
        }
        return BackupArchiveManifest(
            formatVersion = formatVersion,
            createdAtEpochMillis = properties.getProperty("created_at")?.toLongOrNull()
                ?: throw BackupFormatException("备份时间无效"),
            logicalEpochDay = properties.getProperty("logical_epoch_day")?.toLongOrNull()
                ?: throw BackupFormatException("备份日期无效"),
            appVersionName = properties.getProperty("app_version").orEmpty(),
        )
    }

    private fun validateStudyReferences(snapshot: AppBackupSnapshot, extracted: File?) {
        snapshot.studyDocuments.forEach { document ->
            validateRelativePath(document.snapshotId)
            validateRelativePath(document.relativePath)
            if (extracted != null) {
                val root = extracted.canonicalFile
                val source = File(File(root, document.snapshotId), document.relativePath).canonicalFile
                if (!source.toPath().startsWith(root.toPath()) || !source.isFile) {
                    throw BackupFormatException("学习资料“${document.title}”缺少正文文件")
                }
            }
        }
    }

    private fun normalizeEntryName(raw: String): String {
        val name = raw.replace('\\', '/')
        validateRelativePath(name.removeSuffix("/"))
        return name
    }

    private fun validateRelativePath(path: String) {
        val pieces = path.replace('\\', '/').split('/')
        if (path.isBlank() || path.startsWith('/') || pieces.any { it.isBlank() || it == "." || it == ".." }) {
            throw BackupFormatException("备份中存在不安全的文件路径")
        }
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        copyLimited(output, limit.toLong())
        return output.toByteArray()
    }

    private fun ZipInputStream.drainLimited(remainingLimit: Long): Long =
        copyLimited(OutputStreamSink, remainingLimit)

    private fun ZipInputStream.copyLimited(output: java.io.OutputStream, remainingLimit: Long): Long {
        if (remainingLimit < 0L) throw BackupFormatException("备份解压后的大小异常")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > remainingLimit) throw BackupFormatException("备份解压后的大小异常")
            output.write(buffer, 0, count)
        }
        return total
    }

    /** 让二进制编码器 flush 但不把外层 ZipOutputStream 一起关闭。 */
    private fun ZipOutputStream.nonClosing(): java.io.OutputStream = object : java.io.OutputStream() {
        override fun write(value: Int) = this@nonClosing.write(value)
        override fun write(buffer: ByteArray, offset: Int, length: Int) =
            this@nonClosing.write(buffer, offset, length)
        override fun flush() = this@nonClosing.flush()
        override fun close() = flush()
    }

    private object OutputStreamSink : java.io.OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}
