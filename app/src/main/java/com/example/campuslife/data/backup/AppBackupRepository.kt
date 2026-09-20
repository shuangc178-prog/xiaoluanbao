package com.example.campuslife.data.backup

import android.content.Context
import com.example.campuslife.data.card.CardCollectionStore
import com.example.campuslife.data.checkin.CheckInStore
import com.example.campuslife.data.journal.JournalStore
import com.example.campuslife.data.plan.DailyPlanStore
import com.example.campuslife.data.reminder.ReminderStore
import com.example.campuslife.data.study.StudyStore
import com.example.campuslife.data.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.example.campuslife.data.checkin.checkInEpochDay

data class RestoreBackupResult(
    val manifest: BackupArchiveManifest,
    /** 覆盖前自动留下的本机安全副本。 */
    val safetyBackupFile: File,
)

/** 汇总各 DataStore 与学习资料文件，生成/恢复一份语义化完整备份。 */
class AppBackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val checkInStore = CheckInStore(appContext)
    private val planStore = DailyPlanStore(appContext)
    private val journalStore = JournalStore(appContext)
    private val cardStore = CardCollectionStore(appContext)
    private val studyStore = StudyStore(appContext)
    private val themeStore = ThemeStore(appContext)
    private val reminderStore = ReminderStore(appContext)

    suspend fun createTemporaryBackup(
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): Pair<File, CreatedBackup> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val directory = File(appContext.cacheDir, "cloud_backup_outgoing").also(File::mkdirs)
            val destination = File(directory, BackupArchive.fileName(createdAtEpochMillis))
            destination to createBackupLocked(destination, createdAtEpochMillis)
        }
    }

    suspend fun restoreBackup(file: File): RestoreBackupResult = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val restoreId = UUID.randomUUID().toString()
            val stagingStudy = File(appContext.cacheDir, "restore_study_$restoreId")
            val read = BackupArchive.read(file, stagingStudy)
            validateSnapshot(read.snapshot)

            val currentSnapshot = loadSnapshot()
            val safetyDirectory = File(appContext.filesDir, "recovery_safety").also(File::mkdirs)
            val safetyName = "before_restore_" +
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) +
                ".xlbak"
            val safetyFile = File(safetyDirectory, safetyName)
            createBackupLocked(
                destination = safetyFile,
                createdAtEpochMillis = System.currentTimeMillis(),
                snapshot = currentSnapshot,
            )

            val imports = File(appContext.filesDir, STUDY_IMPORTS_DIRECTORY)
            val oldImports = File(appContext.filesDir, "${STUDY_IMPORTS_DIRECTORY}.before_restore_$restoreId")
            try {
                moveDirectoryAside(imports, oldImports)
                moveDirectoryIntoPlace(stagingStudy, imports)
                applySnapshot(read.snapshot)
                reminderStore.resetAfterDataRestore()
                oldImports.deleteRecursively()
                trimSafetyBackups(safetyDirectory)
                RestoreBackupResult(read.manifest, safetyFile)
            } catch (error: Exception) {
                // DataStore 没有跨文件事务；失败时尽最大努力把各模块与资料目录一起回滚。
                runCatching { applySnapshot(currentSnapshot) }
                runCatching {
                    imports.deleteRecursively()
                    if (oldImports.exists()) moveDirectoryIntoPlace(oldImports, imports)
                }
                throw BackupFormatException("恢复没有完成，原数据已尝试保留", error)
            } finally {
                stagingStudy.deleteRecursively()
                oldImports.deleteRecursively()
            }
        }
    }

    private suspend fun createBackupLocked(
        destination: File,
        createdAtEpochMillis: Long,
    ): CreatedBackup = createBackupLocked(
        destination = destination,
        createdAtEpochMillis = createdAtEpochMillis,
        snapshot = loadSnapshot(),
    )

    private fun createBackupLocked(
        destination: File,
        createdAtEpochMillis: Long,
        snapshot: AppBackupSnapshot,
    ): CreatedBackup {
        val manifest = BackupArchiveManifest(
            formatVersion = BackupArchive.FORMAT_VERSION,
            createdAtEpochMillis = createdAtEpochMillis,
            logicalEpochDay = ZonedDateTime.now().checkInEpochDay(),
            appVersionName = installedVersionName(),
        )
        BackupArchive.write(
            destination = destination,
            snapshot = snapshot,
            manifest = manifest,
            studyImportsDirectory = File(appContext.filesDir, STUDY_IMPORTS_DIRECTORY),
        )
        if (destination.length() > BackupArchive.MAX_REMOTE_FILE_BYTES) {
            destination.delete()
            throw BackupFormatException("备份超过坚果云 WebDAV 的 500MB 单文件限制")
        }
        return CreatedBackup(
            fileName = destination.name,
            fileSize = destination.length(),
            sha256 = BackupArchive.sha256(destination),
            manifest = manifest,
        )
    }

    private suspend fun loadSnapshot(): AppBackupSnapshot = AppBackupSnapshot(
        checkIn = checkInStore.snapshot(),
        plans = planStore.snapshot(),
        journal = journalStore.snapshot(),
        cards = cardStore.snapshot(),
        studyDocuments = studyStore.snapshot(),
        darkTheme = themeStore.snapshot(),
        studyTaskStates = studyStore.taskStateSnapshot(),
    )

    private suspend fun applySnapshot(snapshot: AppBackupSnapshot) {
        checkInStore.replaceAll(snapshot.checkIn)
        planStore.replaceAll(snapshot.plans)
        journalStore.replaceAll(snapshot.journal)
        cardStore.replaceAll(snapshot.cards)
        studyStore.replaceAll(snapshot.studyDocuments)
        studyStore.replaceTaskStates(snapshot.studyTaskStates)
        themeStore.restore(snapshot.darkTheme)
    }

    private fun validateSnapshot(snapshot: AppBackupSnapshot) {
        if (snapshot.checkIn.records.groupBy { it.epochDay }.any { it.value.size > 1 }) {
            throw BackupFormatException("备份里同一天有多条打卡记录")
        }
        if (snapshot.plans.map { it.epochDay }.distinct().size != snapshot.plans.size) {
            throw BackupFormatException("备份里存在重复的计划日期")
        }
        snapshot.plans.flatMap { it.tasks }.forEach { task ->
            val minute = task.expectedStartMinute
            val manualOrder = task.manualOrder
            if (
                task.title.isBlank() ||
                (minute != null && minute !in 0 until 24 * 60) ||
                (manualOrder != null && manualOrder < 0)
            ) {
                throw BackupFormatException("备份里存在无效计划任务")
            }
        }
        val categoryIds = snapshot.journal.categories.map { it.id }
        if (categoryIds.any(String::isBlank) || categoryIds.distinct().size != categoryIds.size) {
            throw BackupFormatException("备份里的日记分类无效")
        }
        val documentIds = snapshot.studyDocuments.map { it.id }
        if (documentIds.any(String::isBlank) || documentIds.distinct().size != documentIds.size) {
            throw BackupFormatException("备份里的学习资料索引无效")
        }
        val taskStateIds = snapshot.studyTaskStates.map { it.documentId }
        if (
            taskStateIds.any { it.isBlank() || it !in documentIds } ||
            taskStateIds.distinct().size != taskStateIds.size ||
            snapshot.studyTaskStates.any { state ->
                state.sourceFingerprint.isBlank() || state.checkedIndexes.any { it < 0 }
            }
        ) {
            throw BackupFormatException("备份里的学习资料任务状态无效")
        }
    }

    private fun moveDirectoryAside(source: File, destination: File) {
        destination.deleteRecursively()
        if (!source.exists()) return
        moveDirectoryIntoPlace(source, destination)
    }

    private fun moveDirectoryIntoPlace(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (destination.exists() && !destination.deleteRecursively()) {
            throw BackupFormatException("无法替换学习资料目录")
        }
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            if (!source.copyRecursively(destination, overwrite = true)) {
                throw BackupFormatException("无法恢复学习资料")
            }
            if (!source.deleteRecursively()) throw BackupFormatException("无法清理恢复临时文件")
        }
    }

    private fun trimSafetyBackups(directory: File) {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "xlbak" }
            .sortedByDescending(File::lastModified)
            .drop(SAFETY_BACKUP_LIMIT)
            .forEach(File::delete)
    }

    @Suppress("DEPRECATION")
    private fun installedVersionName(): String = appContext.packageManager
        .getPackageInfo(appContext.packageName, 0)
        .versionName
        .orEmpty()
        .ifBlank { "unknown" }

    private companion object {
        const val STUDY_IMPORTS_DIRECTORY = "study_imports"
        const val SAFETY_BACKUP_LIMIT = 3
        val operationMutex = Mutex()
    }
}
